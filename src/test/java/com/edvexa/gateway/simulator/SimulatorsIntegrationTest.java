package com.edvexa.gateway.simulator;

import com.edvexa.gateway.config.TestRedisConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class SimulatorsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderSimulatorService orderSimulatorService;

    @Autowired
    private PaymentSimulatorService paymentSimulatorService;

    @BeforeEach
    void setUp() {
        orderSimulatorService.reset();
        paymentSimulatorService.reset();
    }

    @Test
    void shouldListAndCreateOrders() throws Exception {
        mockMvc.perform(get("/api/v1/simulated/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));

        Map<String, Object> orderPayload = Map.of(
                "amount", 299.99,
                "customerId", "cust-test-1"
        );

        mockMvc.perform(post("/api/v1/simulated/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.amount", is(299.99)))
                .andExpect(jsonPath("$.status", is("CREATED")));
    }

    @Test
    void shouldEnforceOrderSimulatorFailureControls() throws Exception {
        Map<String, Object> configPayload = Map.of(
                "forcedFailureCount", 2,
                "failureStatusCode", 503
        );

        mockMvc.perform(post("/api/v1/simulated/orders/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(configPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forcedFailureCount", is(2)))
                .andExpect(jsonPath("$.failureStatusCode", is(503)));

        // Request 1: fails with 503
        mockMvc.perform(get("/api/v1/simulated/orders"))
                .andExpect(status().isServiceUnavailable());

        // Request 2: fails with 503
        mockMvc.perform(get("/api/v1/simulated/orders"))
                .andExpect(status().isServiceUnavailable());

        // Request 3: succeeds with 200
        mockMvc.perform(get("/api/v1/simulated/orders"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldProcessPaymentAndRespectFailureOverrides() throws Exception {
        Map<String, Object> paymentPayload = Map.of(
                "amount", 75.00,
                "currency", "USD",
                "paymentMethod", "visa_test"
        );

        mockMvc.perform(post("/api/v1/simulated/payments/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("SUCCEEDED")));

        // Configure deterministic failure with 504 Gateway Timeout simulation
        Map<String, Object> failConfig = Map.of(
                "forcedFailureCount", 1,
                "failureStatusCode", 504
        );

        mockMvc.perform(post("/api/v1/simulated/payments/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failConfig)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/simulated/payments/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentPayload)))
                .andExpect(status().isGatewayTimeout());

        // Reset and verify next call succeeds
        mockMvc.perform(post("/api/v1/simulated/payments/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forcedFailureCount", is(0)));

        mockMvc.perform(post("/api/v1/simulated/payments/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentPayload)))
                .andExpect(status().isCreated());
    }
}
