package com.edvexa.gateway.observability;

import com.edvexa.gateway.observability.dto.GatewayMetricsDto;
import com.edvexa.gateway.observability.dto.LatencyMetricsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayMetricsServiceTest {

    @Mock
    private GatewayRequestLogRepository logRepository;

    private GatewayMetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new GatewayMetricsService(logRepository);
    }

    @Test
    @DisplayName("calculateLatencyMetrics returns empty for empty or null list")
    void testCalculateLatencyMetrics_empty() {
        LatencyMetricsDto empty1 = metricsService.calculateLatencyMetrics(null);
        LatencyMetricsDto empty2 = metricsService.calculateLatencyMetrics(List.of());

        assertThat(empty1.avgMs()).isEqualTo(0.0);
        assertThat(empty2.p99Ms()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("calculateLatencyMetrics computes accurate percentiles, min, max, and avg")
    void testCalculateLatencyMetrics_percentiles() {
        // 100 sample latencies from 1 to 100 ms
        List<Long> latencies = new ArrayList<>();
        for (long i = 1; i <= 100; i++) {
            latencies.add(i);
        }

        LatencyMetricsDto result = metricsService.calculateLatencyMetrics(latencies);

        assertThat(result.minMs()).isEqualTo(1);
        assertThat(result.maxMs()).isEqualTo(100);
        assertThat(result.avgMs()).isEqualTo(50.5);
        assertThat(result.p50Ms()).isEqualTo(50.5);
        assertThat(result.p90Ms()).isEqualTo(90.1);
        assertThat(result.p95Ms()).isEqualTo(95.05);
        assertThat(result.p99Ms()).isEqualTo(99.01);
    }

    @Test
    @DisplayName("getMetricsSummary aggregates totals, outcomes, retries, and breakdowns")
    void testGetMetricsSummary() {
        when(logRepository.count()).thenReturn(100L);
        when(logRepository.countByOutcomeCategory("SUCCESS")).thenReturn(80L);
        when(logRepository.countByOutcomeCategory("CLIENT_ERROR")).thenReturn(10L);
        when(logRepository.countByOutcomeCategory("SERVER_ERROR")).thenReturn(5L);
        when(logRepository.countByOutcomeCategory("TIMEOUT")).thenReturn(2L);
        when(logRepository.countByOutcomeCategory("CIRCUIT_OPEN")).thenReturn(2L);
        when(logRepository.countByOutcomeCategory("RATE_LIMITED")).thenReturn(1L);
        when(logRepository.sumTotalRetries()).thenReturn(7L);
        when(logRepository.findAllLatencies()).thenReturn(List.of(10L, 20L, 30L));
        when(logRepository.countRequestsByService()).thenReturn(List.<Object[]>of(new Object[]{"srv-orders", 60L}));
        when(logRepository.countRequestsByClient()).thenReturn(List.<Object[]>of(new Object[]{"client-1", 40L}));
        when(logRepository.countRequestsByOutcome()).thenReturn(List.<Object[]>of(new Object[]{"SUCCESS", 80L}));

        GatewayMetricsDto summary = metricsService.getMetricsSummary();

        assertThat(summary.totalRequests()).isEqualTo(100L);
        assertThat(summary.successCount()).isEqualTo(80L);
        assertThat(summary.failureCount()).isEqualTo(20L);
        assertThat(summary.clientErrorCount()).isEqualTo(10L);
        assertThat(summary.serverErrorCount()).isEqualTo(5L);
        assertThat(summary.timeoutCount()).isEqualTo(2L);
        assertThat(summary.circuitBreakerOpenCount()).isEqualTo(2L);
        assertThat(summary.rateLimitedCount()).isEqualTo(1L);
        assertThat(summary.retryCount()).isEqualTo(7L);
        assertThat(summary.latency().minMs()).isEqualTo(10L);
        assertThat(summary.latency().maxMs()).isEqualTo(30L);
        assertThat(summary.requestsByService()).containsEntry("srv-orders", 60L);
        assertThat(summary.requestsByClient()).containsEntry("client-1", 40L);
        assertThat(summary.requestsByOutcome()).containsEntry("SUCCESS", 80L);
    }
}
