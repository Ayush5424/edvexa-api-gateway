package com.edvexa.gateway.observability;

import com.edvexa.gateway.observability.dto.GatewayMetricsDto;
import com.edvexa.gateway.observability.dto.LatencyMetricsDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class GatewayMetricsService {

    private final GatewayRequestLogRepository logRepository;

    public GatewayMetricsService(GatewayRequestLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    public GatewayMetricsDto getMetricsSummary() {
        long totalRequests = logRepository.count();
        long successCount = logRepository.countByOutcomeCategory("SUCCESS");
        long clientErrorCount = logRepository.countByOutcomeCategory("CLIENT_ERROR");
        long serverErrorCount = logRepository.countByOutcomeCategory("SERVER_ERROR");
        long timeoutCount = logRepository.countByOutcomeCategory("TIMEOUT");
        long circuitBreakerOpenCount = logRepository.countByOutcomeCategory("CIRCUIT_OPEN");
        long rateLimitedCount = logRepository.countByOutcomeCategory("RATE_LIMITED");
        long failureCount = totalRequests - successCount;

        long retryCount = logRepository.sumTotalRetries();

        List<Long> latencies = logRepository.findAllLatencies();
        LatencyMetricsDto latency = calculateLatencyMetrics(latencies);

        Map<String, Long> requestsByService = new HashMap<>();
        for (Object[] row : logRepository.countRequestsByService()) {
            if (row != null && row.length == 2 && row[0] != null) {
                requestsByService.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        Map<String, Long> requestsByClient = new HashMap<>();
        for (Object[] row : logRepository.countRequestsByClient()) {
            if (row != null && row.length == 2 && row[0] != null) {
                requestsByClient.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        Map<String, Long> requestsByOutcome = new HashMap<>();
        for (Object[] row : logRepository.countRequestsByOutcome()) {
            if (row != null && row.length == 2 && row[0] != null) {
                requestsByOutcome.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }

        return new GatewayMetricsDto(
                totalRequests,
                successCount,
                failureCount,
                clientErrorCount,
                serverErrorCount,
                retryCount,
                timeoutCount,
                circuitBreakerOpenCount,
                rateLimitedCount,
                latency,
                requestsByService,
                requestsByClient,
                requestsByOutcome
        );
    }

    public LatencyMetricsDto calculateLatencyMetrics(List<Long> latencies) {
        if (latencies == null || latencies.isEmpty()) {
            return LatencyMetricsDto.empty();
        }

        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);

        long min = sorted.get(0);
        long max = sorted.get(sorted.size() - 1);
        double sum = 0;
        for (long l : sorted) {
            sum += l;
        }
        double avg = Math.round((sum / sorted.size()) * 100.0) / 100.0;

        double p50 = getPercentile(sorted, 50.0);
        double p90 = getPercentile(sorted, 90.0);
        double p95 = getPercentile(sorted, 95.0);
        double p99 = getPercentile(sorted, 99.0);

        return new LatencyMetricsDto(avg, min, max, p50, p90, p95, p99);
    }

    private double getPercentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) return 0.0;
        if (sorted.size() == 1) return sorted.get(0).doubleValue();
        double index = (percentile / 100.0) * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sorted.get(lower).doubleValue();
        }
        double weight = index - lower;
        double value = sorted.get(lower) * (1 - weight) + sorted.get(upper) * weight;
        return Math.round(value * 100.0) / 100.0;
    }
}
