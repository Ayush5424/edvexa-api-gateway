package com.edvexa.gateway.resilience;

import com.edvexa.gateway.common.error.CircuitBreakerOpenException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerServiceTest {

    private CircuitBreakerService circuitBreakerService;

    @BeforeEach
    void setUp() {
        // Configure with small thresholds for rapid testing:
        // slidingWindowSize=4, minimumNumberOfCalls=2, failureRate=50%, waitDuration=100ms, halfOpenCalls=2
        circuitBreakerService = new CircuitBreakerService(
                4,
                2,
                50.0f,
                Duration.ofMillis(150),
                2
        );
    }

    @Test
    @DisplayName("Should initialize in CLOSED state and permit calls")
    void shouldInitializeInClosedState() {
        String serviceId = "order-srv";

        assertThat(circuitBreakerService.getCircuitBreakerState(serviceId)).isEqualTo("CLOSED");
        // Should not throw
        circuitBreakerService.checkCallPermitted(serviceId, "Order Service");
    }

    @Test
    @DisplayName("Should transition from CLOSED to OPEN when failure threshold is exceeded")
    void shouldTransitionToOpenOnFailures() {
        String serviceId = "payment-srv";

        // Minimum calls = 2; Record 2 failures -> 100% failure rate >= 50%
        circuitBreakerService.recordFailure(serviceId, 10, new RuntimeException("Downstream 503"));
        circuitBreakerService.recordFailure(serviceId, 10, new RuntimeException("Downstream 503"));

        assertThat(circuitBreakerService.getCircuitBreakerState(serviceId)).isEqualTo("OPEN");

        assertThatThrownBy(() -> circuitBreakerService.checkCallPermitted(serviceId, "Payment Service"))
                .isInstanceOf(CircuitBreakerOpenException.class)
                .hasMessageContaining("Payment Service");
    }

    @Test
    @DisplayName("Should transition from OPEN to HALF_OPEN after wait duration and recover to CLOSED on success")
    void shouldTransitionToHalfOpenAndRecoverToClosed() throws InterruptedException {
        String serviceId = "inventory-srv";

        // Trip breaker to OPEN
        circuitBreakerService.recordFailure(serviceId, 10, new RuntimeException("Downstream 500"));
        circuitBreakerService.recordFailure(serviceId, 10, new RuntimeException("Downstream 500"));
        assertThat(circuitBreakerService.getCircuitBreakerState(serviceId)).isEqualTo("OPEN");

        // Wait for open wait duration (150ms) to elapse
        Thread.sleep(200);

        // Transition to HALF_OPEN occurs automatically or upon check
        CircuitBreaker cb = circuitBreakerService.getCircuitBreaker(serviceId);
        // Force evaluation if needed
        assertThat(cb.getState()).isIn(CircuitBreaker.State.HALF_OPEN, CircuitBreaker.State.OPEN);

        // Record 2 successful trial calls in HALF_OPEN
        circuitBreakerService.recordSuccess(serviceId, 10);
        circuitBreakerService.recordSuccess(serviceId, 10);

        // Breaker should recover to CLOSED
        assertThat(circuitBreakerService.getCircuitBreakerState(serviceId)).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("Should reset circuit breaker back to CLOSED state on manual reset")
    void shouldResetCircuitBreaker() {
        String serviceId = "shipping-srv";

        circuitBreakerService.recordFailure(serviceId, 10, new RuntimeException("Failure"));
        circuitBreakerService.recordFailure(serviceId, 10, new RuntimeException("Failure"));
        assertThat(circuitBreakerService.getCircuitBreakerState(serviceId)).isEqualTo("OPEN");

        circuitBreakerService.reset(serviceId);
        assertThat(circuitBreakerService.getCircuitBreakerState(serviceId)).isEqualTo("CLOSED");
    }
}
