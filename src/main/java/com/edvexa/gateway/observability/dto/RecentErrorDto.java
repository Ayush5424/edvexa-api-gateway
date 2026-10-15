package com.edvexa.gateway.observability.dto;

import com.edvexa.gateway.observability.GatewayRequestLogEntity;

import java.time.Instant;

public record RecentErrorDto(
        Long id,
        String correlationId,
        String clientId,
        String serviceId,
        String httpMethod,
        String requestPath,
        int statusCode,
        long latencyMs,
        int retryCount,
        String circuitBreakerState,
        String outcomeCategory,
        String errorMessage,
        Instant timestamp
) {
    public static RecentErrorDto fromEntity(GatewayRequestLogEntity entity) {
        return new RecentErrorDto(
                entity.getId(),
                entity.getCorrelationId(),
                entity.getClientId(),
                entity.getServiceId(),
                entity.getHttpMethod(),
                entity.getRequestPath(),
                entity.getStatusCode(),
                entity.getLatencyMs(),
                entity.getRetryCount(),
                entity.getCircuitBreakerState(),
                entity.getOutcomeCategory(),
                entity.getErrorMessage(),
                entity.getTimestamp()
        );
    }
}
