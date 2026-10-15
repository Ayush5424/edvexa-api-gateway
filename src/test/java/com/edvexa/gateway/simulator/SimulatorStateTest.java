package com.edvexa.gateway.simulator;

import com.edvexa.gateway.simulator.dto.SimulatorConfigDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatorStateTest {

    private SimulatorState state;

    @BeforeEach
    void setUp() {
        state = new SimulatorState("TestService");
    }

    @Test
    void shouldExecuteCleanlyWhenNoFailuresConfigured() {
        state.executeSimulation();
        assertThat(state.getStatus().get("totalRequests")).isEqualTo(1L);
        assertThat(state.getStatus().get("totalFailures")).isEqualTo(0L);
    }

    @Test
    void shouldEnforceArtificialDelay() {
        state.updateConfig(new SimulatorConfigDto(100, null, null, null));

        long start = System.currentTimeMillis();
        state.executeSimulation();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isGreaterThanOrEqualTo(90);
    }

    @Test
    void shouldEnforceDeterministicForcedFailures() {
        state.updateConfig(new SimulatorConfigDto(0, 0.0, 503, 2));

        // Call 1: fails
        assertThatThrownBy(() -> state.executeSimulation())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503");

        // Call 2: fails
        assertThatThrownBy(() -> state.executeSimulation())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503");

        // Call 3: succeeds
        state.executeSimulation();

        assertThat(state.getStatus().get("totalRequests")).isEqualTo(3L);
        assertThat(state.getStatus().get("totalFailures")).isEqualTo(2L);
        assertThat(state.getForcedFailureCount()).isEqualTo(0);
    }

    @Test
    void shouldFailWhenFailureRateIsOne() {
        state.updateConfig(new SimulatorConfigDto(0, 1.0, 500, 0));

        assertThatThrownBy(() -> state.executeSimulation())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("500");

        assertThat(state.getStatus().get("totalFailures")).isEqualTo(1L);
    }

    @Test
    void shouldResetConfigurationAndMetrics() {
        state.updateConfig(new SimulatorConfigDto(500, 0.5, 504, 5));
        state.reset();

        assertThat(state.getDelayMs()).isEqualTo(0);
        assertThat(state.getFailureRate()).isEqualTo(0.0);
        assertThat(state.getFailureStatusCode()).isEqualTo(500);
        assertThat(state.getForcedFailureCount()).isEqualTo(0);
        assertThat(state.getStatus().get("totalRequests")).isEqualTo(0L);
    }
}
