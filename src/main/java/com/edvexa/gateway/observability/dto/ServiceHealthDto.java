package com.edvexa.gateway.observability.dto;

public record ServiceHealthDto(
        String serviceId,
        String name,
        String pathPrefix,
        String baseUrl,
        boolean enabled,
        String circuitBreakerState,
        float failureRate,
        float slowCallRate,
        int numberOfBufferedCalls,
        int numberOfFailedCalls,
        int numberOfSuccessfulCalls,
        long numberOfNotPermittedCalls,
        int connectionTimeoutMs,
        int responseTimeoutMs,
        String status
) {}
