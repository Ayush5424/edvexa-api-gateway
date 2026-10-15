package com.edvexa.gateway.resilience;

import com.edvexa.gateway.config.TestRedisConfig;
import com.edvexa.gateway.observability.GatewayRequestLogEntity;
import com.edvexa.gateway.observability.GatewayRequestLogRepository;
import com.edvexa.gateway.registry.ServiceEntity;
import com.edvexa.gateway.registry.ServiceRepository;
import com.edvexa.gateway.security.apikey.ApiKeyRepository;
import com.edvexa.gateway.security.apikey.ApiKeyService;
import com.edvexa.gateway.security.apikey.dto.ApiKeyCreatedResponse;
import com.edvexa.gateway.security.apikey.dto.CreateApiKeyRequest;
import com.edvexa.gateway.simulator.OrderSimulatorService;
import com.edvexa.gateway.simulator.dto.SimulatorConfigDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class ResilienceIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private GatewayRequestLogRepository logRepository;

    @Autowired
    private OrderSimulatorService orderSimulatorService;

    @Autowired
    private CircuitBreakerService circuitBreakerService;

    private String apiKey;
    private String serviceId;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
        serviceRepository.deleteAll();
        apiKeyRepository.deleteAll();
        orderSimulatorService.reset();
        circuitBreakerService.resetAll();

        ApiKeyCreatedResponse keyResponse = apiKeyService.createApiKey(
                new CreateApiKeyRequest("Resilience-Test-Client", 1000)
        );
        apiKey = keyResponse.getApiKey();

        ServiceEntity orderService = new ServiceEntity(
                "srv-resilience-orders",
                "resilience-order-service",
                "http://localhost:" + port + "/api/v1/simulated/orders",
                "/orders",
                2000,
                3000,
                true
        );
        serviceRepository.save(orderService);
        serviceId = orderService.getId();
    }

    @AfterEach
    void tearDown() {
        orderSimulatorService.reset();
        circuitBreakerService.resetAll();
        logRepository.deleteAll();
        serviceRepository.deleteAll();
        apiKeyRepository.deleteAll();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        return headers;
    }

    @Test
    @DisplayName("Should return 504 Gateway Timeout when downstream exceeds response timeout")
    void shouldTimeoutWhenDownstreamExceedsResponseTimeout() {
        // Update service response timeout to 400ms
        ServiceEntity service = serviceRepository.findById(serviceId).orElseThrow();
        service.setResponseTimeoutMs(400);
        serviceRepository.save(service);

        // Inject 1200ms delay into order simulator
        orderSimulatorService.updateConfig(new SimulatorConfigDto(1200, 0.0, 500, 0));

        HttpHeaders headers = createHeaders();
        headers.set("X-Correlation-ID", "corr-timeout-test");

        ResponseEntity<String> response = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).contains("timed out or was unreachable");

        // Verify audit log recorded TIMEOUT outcome
        List<GatewayRequestLogEntity> logs = logRepository.findByCorrelationId("corr-timeout-test");
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getStatusCode()).isEqualTo(504);
        assertThat(logs.get(0).getOutcomeCategory()).isEqualTo("TIMEOUT");
    }

    @Test
    @DisplayName("Should retry transient downstream 503 failure and succeed with recorded retry count")
    void shouldRetryTransientFailureAndSucceed() {
        // Fail exactly 1 call with 503, then succeed on retry
        orderSimulatorService.updateConfig(new SimulatorConfigDto(0, 0.0, 503, 1));

        HttpHeaders headers = createHeaders();
        headers.set("X-Correlation-ID", "corr-retry-success");

        ResponseEntity<String> response = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("ORD-101");

        // Verify audit log recorded 1 retry
        List<GatewayRequestLogEntity> logs = logRepository.findByCorrelationId("corr-retry-success");
        assertThat(logs).isNotEmpty();
        GatewayRequestLogEntity log = logs.get(0);
        assertThat(log.getStatusCode()).isEqualTo(200);
        assertThat(log.getRetryCount()).isEqualTo(1);
        assertThat(log.getOutcomeCategory()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("Should fail fast on 4xx client error without executing retries")
    void shouldFailFastOnClientErrorWithoutRetries() {
        HttpHeaders headers = createHeaders();
        headers.set("X-Correlation-ID", "corr-404-no-retry");

        ResponseEntity<String> response = restTemplate.exchange(
                "/gateway/orders/non-existent-order-id-9999",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        List<GatewayRequestLogEntity> logs = logRepository.findByCorrelationId("corr-404-no-retry");
        assertThat(logs).isNotEmpty();
        GatewayRequestLogEntity log = logs.get(0);
        assertThat(log.getRetryCount()).isEqualTo(0);
        assertThat(log.getOutcomeCategory()).isEqualTo("CLIENT_ERROR");
    }

    @Test
    @DisplayName("Should trip circuit breaker from CLOSED to OPEN on repeated failures and block downstream calls")
    void shouldTripCircuitBreakerToOpenAndBlockDownstreamCalls() {
        // Configure simulator to persistently fail with 500
        orderSimulatorService.updateConfig(new SimulatorConfigDto(0, 0.0, 500, 10));

        // Make requests to trip the circuit breaker (minimum calls=3, failure threshold=50%)
        for (int i = 0; i < 4; i++) {
            restTemplate.exchange("/gateway/orders", HttpMethod.GET, new HttpEntity<>(createHeaders()), String.class);
        }

        // Breaker should now be OPEN
        assertThat(circuitBreakerService.getCircuitBreakerState(serviceId)).isEqualTo("OPEN");

        long requestsBefore = ((Number) orderSimulatorService.getSimulatorState().getStatus().get("totalRequests")).longValue();

        // Call while OPEN -> must be rejected immediately by circuit breaker without reaching downstream
        HttpHeaders headers = createHeaders();
        headers.set("X-Correlation-ID", "corr-cb-blocked");
        ResponseEntity<String> openResponse = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(openResponse.getBody()).contains("Circuit breaker is OPEN");

        // Verify no downstream call was made while OPEN
        long requestsAfter = ((Number) orderSimulatorService.getSimulatorState().getStatus().get("totalRequests")).longValue();
        assertThat(requestsAfter).isEqualTo(requestsBefore);

        // Verify audit log recorded CIRCUIT_OPEN outcome
        List<GatewayRequestLogEntity> logs = logRepository.findByCorrelationId("corr-cb-blocked");
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getOutcomeCategory()).isEqualTo("CIRCUIT_OPEN");
        assertThat(logs.get(0).getCircuitBreakerState()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Should handle concurrent requests through resilience pipeline safely")
    void shouldHandleConcurrentRequestsSafely() throws InterruptedException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ResponseEntity<String> response = restTemplate.exchange(
                            "/gateway/orders",
                            HttpMethod.GET,
                            new HttpEntity<>(createHeaders()),
                            String.class
                    );
                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(threads);
    }
}
