package com.edvexa.gateway.routing;

import com.edvexa.gateway.common.error.CircuitBreakerOpenException;
import com.edvexa.gateway.common.error.DownstreamTimeoutException;
import com.edvexa.gateway.common.error.IdempotencyConflictException;
import com.edvexa.gateway.common.error.ResourceNotFoundException;
import com.edvexa.gateway.common.error.ServiceUnavailableException;
import com.edvexa.gateway.idempotency.IdempotencyCheckResult;
import com.edvexa.gateway.idempotency.IdempotencyService;
import com.edvexa.gateway.observability.CorrelationIdContext;
import com.edvexa.gateway.observability.RequestAuditService;
import com.edvexa.gateway.registry.ServiceEntity;
import com.edvexa.gateway.registry.ServiceRepository;
import com.edvexa.gateway.resilience.CircuitBreakerService;
import com.edvexa.gateway.resilience.DownstreamClientProvider;
import com.edvexa.gateway.resilience.RetryPolicyService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Comparator;
import java.util.List;

@Service
public class GatewayRoutingService {

    private static final Logger log = LoggerFactory.getLogger(GatewayRoutingService.class);

    private final ServiceRepository serviceRepository;
    private final HopByHopHeaderFilter headerFilter;
    private final DownstreamClientProvider clientProvider;
    private final CircuitBreakerService circuitBreakerService;
    private final RetryPolicyService retryPolicyService;
    private final RequestAuditService requestAuditService;
    private final IdempotencyService idempotencyService;

    public GatewayRoutingService(
            ServiceRepository serviceRepository,
            HopByHopHeaderFilter headerFilter,
            DownstreamClientProvider clientProvider,
            CircuitBreakerService circuitBreakerService,
            RetryPolicyService retryPolicyService,
            RequestAuditService requestAuditService,
            IdempotencyService idempotencyService
    ) {
        this.serviceRepository = serviceRepository;
        this.headerFilter = headerFilter;
        this.clientProvider = clientProvider;
        this.circuitBreakerService = circuitBreakerService;
        this.retryPolicyService = retryPolicyService;
        this.requestAuditService = requestAuditService;
        this.idempotencyService = idempotencyService;
    }

    public ResponseEntity<byte[]> forwardRequest(
            HttpServletRequest request,
            String subpath,
            byte[] body,
            String clientId
    ) {
        long startTime = System.currentTimeMillis();
        String correlationId = CorrelationIdContext.get();
        String requestPath = request.getRequestURI();
        String method = request.getMethod();

        // 1. Identify destination service by matching path prefix
        ServiceEntity service = resolveService(subpath);
        if (service == null) {
            long latency = System.currentTimeMillis() - startTime;
            log.warn("Routing failed: no registered service matches route [{}] (subpath [{}]), correlationId={}",
                    requestPath, subpath, correlationId);
            requestAuditService.recordRequest(
                    correlationId, clientId, null, method, requestPath, 404, latency, 0, "CLOSED", "No route found"
            );
            throw new ResourceNotFoundException("No registered service matches route: " + requestPath);
        }

        if (!service.isEnabled()) {
            long latency = System.currentTimeMillis() - startTime;
            log.warn("Routing rejected: service [{}] is disabled, route [{}], correlationId={}",
                    service.getName(), requestPath, correlationId);
            requestAuditService.recordRequest(
                    correlationId, clientId, service.getId(), method, requestPath, 503, latency, 0, "CLOSED", "Service disabled"
            );
            throw new ServiceUnavailableException("Service '" + service.getName() + "' is currently disabled");
        }

        String serviceId = service.getId();

        // 2. Client-scoped Idempotency check for write operations (POST, PUT, PATCH, DELETE)
        boolean isWriteMethod = List.of("POST", "PUT", "PATCH", "DELETE").contains(method.toUpperCase());
        String idempotencyKey = request.getHeader(IdempotencyService.IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = request.getHeader("idempotency-key");
        }
        boolean hasIdempotencyKey = isWriteMethod && idempotencyKey != null && !idempotencyKey.isBlank();

        if (hasIdempotencyKey) {
            String fullPath = request.getQueryString() != null && !request.getQueryString().isBlank()
                    ? requestPath + "?" + request.getQueryString()
                    : requestPath;

            IdempotencyCheckResult checkResult;
            try {
                checkResult = idempotencyService.processIdempotencyKey(
                        clientId, idempotencyKey, method, fullPath, body
                );
            } catch (IdempotencyConflictException ex) {
                long latency = System.currentTimeMillis() - startTime;
                String currentState = circuitBreakerService.getCircuitBreakerState(serviceId);
                requestAuditService.recordRequest(
                        correlationId, clientId, serviceId, method, requestPath, 409, latency, 0, currentState, ex.getMessage()
                );
                throw ex;
            }

            if (checkResult.action() == IdempotencyCheckResult.Action.REPLAY) {
                long latency = System.currentTimeMillis() - startTime;
                String currentState = circuitBreakerService.getCircuitBreakerState(serviceId);
                ResponseEntity<byte[]> cached = checkResult.cachedResponse();
                requestAuditService.recordRequest(
                        correlationId, clientId, serviceId, method, requestPath,
                        cached.getStatusCode().value(), latency, 0, currentState, null
                );
                return cached;
            }
        }

        // 3. Enforce Circuit Breaker state (Fail-fast if OPEN without making downstream calls)
        try {
            circuitBreakerService.checkCallPermitted(serviceId, service.getName());
        } catch (CircuitBreakerOpenException ex) {
            if (hasIdempotencyKey) {
                idempotencyService.abortOperation(clientId, idempotencyKey);
            }
            long latency = System.currentTimeMillis() - startTime;
            requestAuditService.recordRequest(
                    correlationId, clientId, serviceId, method, requestPath, 503, latency, 0, "OPEN", ex.getMessage()
            );
            throw ex;
        }

        // 4. Prepare target URI, headers, and per-service configured HTTP client
        URI targetUri = buildDownstreamUri(service, subpath, request.getQueryString());
        HttpHeaders forwardHeaders = headerFilter.filterHeaders(request.getHeaderNames(), request::getHeaders);
        RestClient restClient = clientProvider.getRestClientForService(service);

        log.info("Routing request correlationId={} serviceId={} method={} to targetUri={}",
                correlationId, serviceId, method, targetUri);

        // 5. Forward request with bounded retry, circuit breaker tracking, and idempotency completion
        int retriesRecorded = 0;
        try {
            RetryPolicyService.RetryExecutionResult<ResponseEntity<byte[]>> executionResult =
                    retryPolicyService.executeWithRetry(serviceId, () -> {
                        long callStart = System.currentTimeMillis();
                        try {
                            RestClient.RequestBodySpec requestSpec = restClient
                                    .method(HttpMethod.valueOf(method))
                                    .uri(targetUri)
                                    .headers(h -> h.addAll(forwardHeaders));

                            if (body != null && body.length > 0 && HttpMethod.valueOf(method) != HttpMethod.GET) {
                                requestSpec.body(body);
                            }

                            ResponseEntity<byte[]> response = requestSpec.exchange((clientReq, clientResp) -> {
                                int rawStatusCode = clientResp.getStatusCode().value();
                                byte[] responseBytes = clientResp.getBody().readAllBytes();
                                HttpHeaders responseHeaders = headerFilter.filterDownstreamResponseHeaders(clientResp.getHeaders());
                                responseHeaders.set("X-Gateway-Service-ID", serviceId);

                                return ResponseEntity.status(rawStatusCode)
                                        .headers(responseHeaders)
                                        .body(responseBytes);
                            });

                            long callDuration = System.currentTimeMillis() - callStart;
                            int status = response.getStatusCode().value();

                            if (status >= 500) {
                                circuitBreakerService.recordFailure(serviceId, callDuration, new RuntimeException("HTTP " + status));
                            } else {
                                circuitBreakerService.recordSuccess(serviceId, callDuration);
                            }

                            return response;

                        } catch (Exception ex) {
                            long callDuration = System.currentTimeMillis() - callStart;
                            circuitBreakerService.recordFailure(serviceId, callDuration, ex);
                            throw ex;
                        }
                    });

            retriesRecorded = executionResult.retries();
            ResponseEntity<byte[]> response = executionResult.result();
            int statusCode = response.getStatusCode().value();

            if (hasIdempotencyKey) {
                if (statusCode < 500) {
                    idempotencyService.completeOperation(
                            clientId, idempotencyKey, statusCode, response.getHeaders(), response.getBody()
                    );
                } else {
                    idempotencyService.abortOperation(clientId, idempotencyKey);
                }
            }

            long totalLatency = System.currentTimeMillis() - startTime;
            String currentState = circuitBreakerService.getCircuitBreakerState(serviceId);

            requestAuditService.recordRequest(
                    correlationId, clientId, serviceId, method, requestPath,
                    statusCode, totalLatency, retriesRecorded, currentState, null
            );

            return response;

        } catch (ResourceAccessException ex) {
            if (hasIdempotencyKey) {
                idempotencyService.abortOperation(clientId, idempotencyKey);
            }
            long totalLatency = System.currentTimeMillis() - startTime;
            String currentState = circuitBreakerService.getCircuitBreakerState(serviceId);
            requestAuditService.recordRequest(
                    correlationId, clientId, serviceId, method, requestPath, 504, totalLatency, retriesRecorded, currentState, ex.getMessage()
            );
            throw new DownstreamTimeoutException("Downstream request timed out or was unreachable: " + ex.getMessage());

        } catch (HttpStatusCodeException ex) {
            if (hasIdempotencyKey) {
                if (ex.getStatusCode().value() < 500) {
                    idempotencyService.completeOperation(
                            clientId, idempotencyKey, ex.getStatusCode().value(), ex.getResponseHeaders(), ex.getResponseBodyAsByteArray()
                    );
                } else {
                    idempotencyService.abortOperation(clientId, idempotencyKey);
                }
            }
            long totalLatency = System.currentTimeMillis() - startTime;
            String currentState = circuitBreakerService.getCircuitBreakerState(serviceId);
            int statusCode = ex.getStatusCode().value();

            requestAuditService.recordRequest(
                    correlationId, clientId, serviceId, method, requestPath, statusCode, totalLatency, retriesRecorded, currentState, ex.getMessage()
            );

            HttpHeaders cleanHeaders = headerFilter.filterDownstreamResponseHeaders(ex.getResponseHeaders());
            return ResponseEntity.status(statusCode)
                    .headers(cleanHeaders)
                    .body(ex.getResponseBodyAsByteArray());

        } catch (CircuitBreakerOpenException ex) {
            if (hasIdempotencyKey) {
                idempotencyService.abortOperation(clientId, idempotencyKey);
            }
            long totalLatency = System.currentTimeMillis() - startTime;
            requestAuditService.recordRequest(
                    correlationId, clientId, serviceId, method, requestPath, 503, totalLatency, retriesRecorded, "OPEN", ex.getMessage()
            );
            throw ex;

        } catch (Exception ex) {
            if (hasIdempotencyKey) {
                idempotencyService.abortOperation(clientId, idempotencyKey);
            }
            long totalLatency = System.currentTimeMillis() - startTime;
            String currentState = circuitBreakerService.getCircuitBreakerState(serviceId);
            requestAuditService.recordRequest(
                    correlationId, clientId, serviceId, method, requestPath, 500, totalLatency, retriesRecorded, currentState, ex.getMessage()
            );
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }

    ServiceEntity resolveService(String subpath) {
        if (subpath == null || subpath.isBlank()) {
            return null;
        }

        String normalizedPath = normalizePath(subpath);
        List<ServiceEntity> services = serviceRepository.findAll();

        return services.stream()
                .filter(s -> matchesPrefix(normalizedPath, s.getPathPrefix()))
                .max(Comparator.comparingInt(s -> normalizePath(s.getPathPrefix()).length()))
                .orElse(null);
    }

    boolean matchesPrefix(String normalizedPath, String rawPrefix) {
        if (rawPrefix == null || rawPrefix.isBlank()) {
            return false;
        }
        String normalizedPrefix = normalizePath(rawPrefix);
        if (normalizedPrefix.equals("/")) {
            return true;
        }
        return normalizedPath.equals(normalizedPrefix) || normalizedPath.startsWith(normalizedPrefix + "/");
    }

    String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String p = path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    URI buildDownstreamUri(ServiceEntity service, String subpath, String queryString) {
        String base = service.getBaseUrl() != null ? service.getBaseUrl().trim() : "";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        String normalizedPrefix = normalizePath(service.getPathPrefix());
        String normalizedSubpath = normalizePath(subpath);

        String forwardPath;
        if (base.endsWith(normalizedPrefix)) {
            // Base URL already includes the path prefix at its end
            // (e.g., base = "http://localhost:8080/api/v1/simulated/orders", prefix = "/orders")
            forwardPath = normalizedSubpath.substring(normalizedPrefix.length());
        } else {
            // Base URL is host/port only or does not end with prefix
            // (e.g., base = "http://localhost:8081", prefix = "/test") -> downstream expects "/test"
            forwardPath = subpath.startsWith("/") ? subpath : "/" + subpath;
        }

        if (!forwardPath.isEmpty() && !forwardPath.startsWith("/")) {
            forwardPath = "/" + forwardPath;
        }

        StringBuilder uriBuilder = new StringBuilder(base).append(forwardPath);
        if (queryString != null && !queryString.isBlank()) {
            uriBuilder.append("?").append(queryString);
        }

        return URI.create(uriBuilder.toString());
    }
}
