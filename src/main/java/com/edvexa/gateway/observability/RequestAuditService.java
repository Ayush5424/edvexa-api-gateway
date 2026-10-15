package com.edvexa.gateway.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class RequestAuditService {

    private static final Logger log = LoggerFactory.getLogger("com.edvexa.gateway.audit");

    private final GatewayRequestLogRepository logRepository;
    private MeterRegistry meterRegistry;

    public RequestAuditService(GatewayRequestLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Autowired(required = false)
    public void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GatewayRequestLogEntity recordRequest(
            String correlationId,
            String clientId,
            String serviceId,
            String httpMethod,
            String requestPath,
            int statusCode,
            long latencyMs,
            int retryCount,
            String circuitBreakerState,
            String errorMessage
    ) {
        String outcome = determineOutcomeCategory(statusCode, circuitBreakerState, errorMessage);

        // Structured log entry (omits credentials, bodies, and sensitive headers)
        log.info("GATEWAY_REQUEST: correlationId={} clientId={} serviceId={} method={} path={} status={} latencyMs={} retries={} cbState={} outcome={}",
                correlationId,
                clientId != null ? clientId : "anonymous",
                serviceId != null ? serviceId : "none",
                httpMethod,
                requestPath,
                statusCode,
                latencyMs,
                retryCount,
                circuitBreakerState != null ? circuitBreakerState : "N/A",
                outcome
        );

        if (meterRegistry != null) {
            try {
                String safeService = serviceId != null ? serviceId : "none";
                String safeClient = clientId != null ? clientId : "anonymous";
                meterRegistry.counter("gateway.requests.total", "service", safeService, "method", httpMethod, "outcome", outcome).increment();
                meterRegistry.timer("gateway.requests.latency", "service", safeService).record(latencyMs, TimeUnit.MILLISECONDS);
                if (retryCount > 0) {
                    meterRegistry.counter("gateway.requests.retries", "service", safeService).increment(retryCount);
                }
                if ("TIMEOUT".equalsIgnoreCase(outcome)) {
                    meterRegistry.counter("gateway.requests.timeouts", "service", safeService).increment();
                } else if ("CIRCUIT_OPEN".equalsIgnoreCase(outcome)) {
                    meterRegistry.counter("gateway.requests.circuit_open", "service", safeService).increment();
                } else if ("RATE_LIMITED".equalsIgnoreCase(outcome)) {
                    meterRegistry.counter("gateway.requests.rate_limited", "client", safeClient).increment();
                }
            } catch (Exception ex) {
                log.warn("Failed to record actuator metric: {}", ex.getMessage());
            }
        }

        GatewayRequestLogEntity entity = new GatewayRequestLogEntity(
                correlationId,
                clientId,
                serviceId,
                httpMethod,
                requestPath,
                statusCode,
                latencyMs,
                retryCount,
                circuitBreakerState,
                outcome,
                truncateErrorMessage(errorMessage)
        );

        try {
            return logRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to persist gateway request log for correlationId={}: {}", correlationId, e.getMessage());
            return entity;
        }
    }

    public String determineOutcomeCategory(int statusCode, String circuitBreakerState, String errorMessage) {
        if (errorMessage != null && errorMessage.contains("Circuit breaker is OPEN")) {
            return "CIRCUIT_OPEN";
        }
        if (statusCode == 504 || (errorMessage != null && errorMessage.toLowerCase().contains("timeout"))) {
            return "TIMEOUT";
        }
        if (statusCode == 429 || "RATE_LIMITED".equalsIgnoreCase(errorMessage)) {
            return "RATE_LIMITED";
        }
        if ("OPEN".equalsIgnoreCase(circuitBreakerState) && statusCode == 503) {
            return "CIRCUIT_OPEN";
        }
        if (statusCode >= 200 && statusCode < 400) {
            return "SUCCESS";
        }
        if (statusCode >= 400 && statusCode < 500) {
            return "CLIENT_ERROR";
        }
        return "SERVER_ERROR";
    }

    private String truncateErrorMessage(String error) {
        if (error == null) return null;
        return error.length() > 500 ? error.substring(0, 500) : error;
    }
}
