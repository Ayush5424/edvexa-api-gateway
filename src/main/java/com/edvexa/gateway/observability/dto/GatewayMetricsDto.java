package com.edvexa.gateway.observability.dto;

import java.util.Map;

public record GatewayMetricsDto(
        long totalRequests,
        long successCount,
        long failureCount,
        long clientErrorCount,
        long serverErrorCount,
        long retryCount,
        long timeoutCount,
        long circuitBreakerOpenCount,
        long rateLimitedCount,
        LatencyMetricsDto latency,
        Map<String, Long> requestsByService,
        Map<String, Long> requestsByClient,
        Map<String, Long> requestsByOutcome
) {}
