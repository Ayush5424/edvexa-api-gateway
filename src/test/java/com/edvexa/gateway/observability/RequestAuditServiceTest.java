package com.edvexa.gateway.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RequestAuditServiceTest {

    @Mock
    private GatewayRequestLogRepository logRepository;

    @InjectMocks
    private RequestAuditService auditService;

    @Test
    void shouldCategorizeOutcomesCorrectly() {
        assertThat(auditService.determineOutcomeCategory(200, "CLOSED", null)).isEqualTo("SUCCESS");
        assertThat(auditService.determineOutcomeCategory(201, "CLOSED", null)).isEqualTo("SUCCESS");
        assertThat(auditService.determineOutcomeCategory(400, "CLOSED", "Validation error")).isEqualTo("CLIENT_ERROR");
        assertThat(auditService.determineOutcomeCategory(404, "CLOSED", "Not found")).isEqualTo("CLIENT_ERROR");
        assertThat(auditService.determineOutcomeCategory(429, "CLOSED", "Rate limit exceeded")).isEqualTo("RATE_LIMITED");
        assertThat(auditService.determineOutcomeCategory(500, "CLOSED", "Internal error")).isEqualTo("SERVER_ERROR");
        assertThat(auditService.determineOutcomeCategory(503, "OPEN", "Circuit breaker is OPEN")).isEqualTo("CIRCUIT_OPEN");
        assertThat(auditService.determineOutcomeCategory(504, "CLOSED", "Downstream timeout")).isEqualTo("TIMEOUT");
    }

    @Test
    void shouldPersistAuditLog() {
        auditService.recordRequest(
                "trace-1",
                "client-1",
                "srv-orders",
                "GET",
                "/gateway/orders",
                200,
                25L,
                0,
                "CLOSED",
                null
        );

        ArgumentCaptor<GatewayRequestLogEntity> captor = ArgumentCaptor.forClass(GatewayRequestLogEntity.class);
        verify(logRepository).save(captor.capture());

        GatewayRequestLogEntity saved = captor.getValue();
        assertThat(saved.getCorrelationId()).isEqualTo("trace-1");
        assertThat(saved.getClientId()).isEqualTo("client-1");
        assertThat(saved.getServiceId()).isEqualTo("srv-orders");
        assertThat(saved.getStatusCode()).isEqualTo(200);
        assertThat(saved.getLatencyMs()).isEqualTo(25L);
        assertThat(saved.getOutcomeCategory()).isEqualTo("SUCCESS");
    }
}
