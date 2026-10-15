package com.edvexa.gateway.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestAuditService {

    private static final Logger log = LoggerFactory.getLogger("com.edvexa.gateway.audit");

    private final GatewayRequestLogRepository logRepository;

    public RequestAuditService(GatewayRequestLogRepository logRepository) {
        this.logRepository = logRepository;
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
        if ("OPEN".equalsIgnoreCase(circuitBreakerState) || (errorMessage != null && errorMessage.contains("Circuit breaker is OPEN"))) {
            return "CIRCUIT_OPEN";
        }
        if (statusCode == 429 || "RATE_LIMITED".equalsIgnoreCase(errorMessage)) {
            return "RATE_LIMITED";
        }
        if (statusCode == 504 || (errorMessage != null && errorMessage.toLowerCase().contains("timeout"))) {
            return "TIMEOUT";
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
