package com.edvexa.gateway.observability;

import com.edvexa.gateway.config.TestRedisConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Transactional
class GatewayRequestLogRepositoryTest {

    @Autowired
    private GatewayRequestLogRepository logRepository;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
    }

    @Test
    void shouldPersistAndRetrieveRequestLog() {
        GatewayRequestLogEntity log = new GatewayRequestLogEntity(
                "corr-12345",
                "client-test",
                "srv-order",
                "POST",
                "/gateway/orders",
                201,
                42L,
                0,
                "CLOSED",
                "SUCCESS",
                null
        );

        GatewayRequestLogEntity saved = logRepository.save(log);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTimestamp()).isNotNull();

        List<GatewayRequestLogEntity> byCorrelation = logRepository.findByCorrelationId("corr-12345");
        assertThat(byCorrelation).hasSize(1);
        assertThat(byCorrelation.get(0).getServiceId()).isEqualTo("srv-order");
        assertThat(byCorrelation.get(0).getOutcomeCategory()).isEqualTo("SUCCESS");

        long successCount = logRepository.countByOutcomeCategory("SUCCESS");
        assertThat(successCount).isEqualTo(1L);

        List<GatewayRequestLogEntity> recent = logRepository.findRecentLogs(Instant.now().minus(1, ChronoUnit.HOURS));
        assertThat(recent).hasSize(1);
    }
}
