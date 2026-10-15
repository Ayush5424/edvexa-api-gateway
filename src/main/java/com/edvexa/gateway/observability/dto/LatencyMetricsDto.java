package com.edvexa.gateway.observability.dto;

public record LatencyMetricsDto(
        double avgMs,
        long minMs,
        long maxMs,
        double p50Ms,
        double p90Ms,
        double p95Ms,
        double p99Ms
) {
    public static LatencyMetricsDto empty() {
        return new LatencyMetricsDto(0.0, 0, 0, 0.0, 0.0, 0.0, 0.0);
    }
}
