package com.edvexa.gateway.routing;

import com.edvexa.gateway.common.error.CircuitBreakerOpenException;
import com.edvexa.gateway.common.error.DownstreamTimeoutException;
import com.edvexa.gateway.common.error.ResourceNotFoundException;
import com.edvexa.gateway.common.error.ServiceUnavailableException;
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

    public GatewayRoutingService(
            ServiceRepository serviceRepository,
            HopByHopHeaderFilter headerFilter,
            DownstreamClientProvider clientProvider,
            CircuitBreakerService circuitBreakerService,
            RetryPolicyService retryPolicyService,
            RequestAuditService requestAuditService
    ) {
        this.serviceRepository = serviceRepository;
        this.headerFilter = headerFilter;
        this.clientProvider = clientProvider;
        this.circuitBreakerService = circuitBreakerService;
        this.retryPolicyService = retryPolicyService;
        this.requestAuditService = requestAuditService;
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
            requestAuditService.recordRequest(
                    correlationId, clientId, null, method, requestPath, 404, latency, 0, "CLOSED", "No route found"
            );
            throw new ResourceNotFoundException("No registered service matches route: " + requestPath);
        }

        if (!service.isEnabled()) {
            long latency = System.currentTimeMillis() - startTime;
            requestAuditService.recordRequest(
                    correlationId, clientId, service.getId(), method, requestPath, 503, latency, 0, "CLOSED", "Service disabled"
            );
            throw new ServiceUnavailableException("Service '" + service.getName() + "' is currently disabled");
        }

        String serviceId = service.getId();

        // 2. Enforce Circuit Breaker state (Fail-fast if OPEN without making downstream calls)
        try {
            circuitBreakerService.checkCallPermitted(serviceId, service.getName());
        } catch (CircuitBreakerOpenException ex) {
            long latency = System.currentTimeMillis() - startTime;
            requestAuditService.recordRequest(
                    correlationId, clientId, serviceId, method, requestPath, 503, latency, 0, "OPEN", ex.getMessage()
            );
            throw ex;
        }

        // 3. Prepare target URI, headers, and per-service configured HTTP client
        URI targetUri = buildDownstreamUri(service, subpath, request.getQueryString());
        HttpHeaders forwardHeaders = headerFilter.filterHeaders(request.getHeaderNames(), request::getHeaders);
        RestClient restClient = clientProvider.getRestClientForService(service);

        log.debug("Routing request correlationId={} to targetUri={}", correlationId, targetUri);

        // 4. Forward request with bounded retry and circuit breaker tracking
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

            long totalLatency = System.currentTimeMillis() - startTime;
            String currentState = circuitBreakerService.getCircuitBreakerState(serviceId);

            requestAuditService.recordRequest(
                    correlationId, clientId, serviceId, method, requestPath,
                    response.getStatusCode().value(), totalLatency, retriesRecorded, currentState, null
            );

            return response;

        } catch (ResourceAccessException ex) {
            long totalLatency = System.currentTimeMillis() - startTime;
            String currentState = circuitBreakerService.getCircuitBreakerState(serviceId);
            requestAuditService.recordRequest(
                    correlationId, clientId, serviceId, method, requestPath, 504, totalLatency, retriesRecorded, currentState, ex.getMessage()
            );
            throw new DownstreamTimeoutException("Downstream request timed out or was unreachable: " + ex.getMessage());

        } catch (HttpStatusCodeException ex) {
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
            long totalLatency = System.currentTimeMillis() - startTime;
            requestAuditService.recordRequest(
                    correlationId, clientId, serviceId, method, requestPath, 503, totalLatency, retriesRecorded, "OPEN", ex.getMessage()
            );
            throw ex;

        } catch (Exception ex) {
            long totalLatency = System.currentTimeMillis() - startTime;
            String currentState = circuitBreakerService.getCircuitBreakerState(serviceId);
            requestAuditService.recordRequest(
                    correlationId, clientId, serviceId, method, requestPath, 500, totalLatency, retriesRecorded, currentState, ex.getMessage()
            );
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }

    private ServiceEntity resolveService(String subpath) {
        String path = subpath.startsWith("/") ? subpath : "/" + subpath;
        List<ServiceEntity> services = serviceRepository.findAll();

        return services.stream()
                .filter(s -> path.startsWith(s.getPathPrefix()))
                .max(Comparator.comparingInt(s -> s.getPathPrefix().length()))
                .orElse(null);
    }

    private URI buildDownstreamUri(ServiceEntity service, String subpath, String queryString) {
        String base = service.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        String path = subpath.startsWith("/") ? subpath : "/" + subpath;
        String prefix = service.getPathPrefix();

        String remaining = path.substring(prefix.length());
        if (!remaining.isEmpty() && !remaining.startsWith("/")) {
            remaining = "/" + remaining;
        }

        StringBuilder uriBuilder = new StringBuilder(base).append(remaining);
        if (queryString != null && !queryString.isBlank()) {
            uriBuilder.append("?").append(queryString);
        }

        return URI.create(uriBuilder.toString());
    }
}
