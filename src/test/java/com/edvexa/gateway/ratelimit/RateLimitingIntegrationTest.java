package com.edvexa.gateway.ratelimit;

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
import org.springframework.http.MediaType;
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
class RateLimitingIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private GatewayRequestLogRepository logRepository;

    @Autowired
    private OrderSimulatorService orderSimulatorService;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
        serviceRepository.deleteAll();
        apiKeyRepository.deleteAll();
        orderSimulatorService.reset();

        ServiceEntity orderService = new ServiceEntity(
                "srv-order-ratelimit",
                "order-service",
                "http://localhost:" + port + "/api/v1/simulated/orders",
                "/orders",
                3000,
                5000,
                true
        );
        serviceRepository.save(orderService);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        logRepository.deleteAll();
        serviceRepository.deleteAll();
        apiKeyRepository.deleteAll();
    }

    @Test
    @DisplayName("Should include rate limit headers on successful gateway requests")
    void shouldIncludeRateLimitHeadersOnNormalRequest() {
        ApiKeyCreatedResponse key = apiKeyService.createApiKey(
                new CreateApiKeyRequest("Partner-App", 10)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", key.getApiKey());

        ResponseEntity<String> response = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(response.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("9");
        assertThat(response.getHeaders().getFirst("X-RateLimit-Reset")).isNotNull();
    }

    @Test
    @DisplayName("Should return 429 with Retry-After when rate limit is exceeded")
    void shouldReturn429WhenLimitExceeded() {
        // Create key with strict limit of 2 requests per minute
        ApiKeyCreatedResponse key = apiKeyService.createApiKey(
                new CreateApiKeyRequest("Strict-Client", 2)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", key.getApiKey());
        headers.set("X-Correlation-ID", "corr-rate-limit-test");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Request 1: OK (remaining: 1)
        ResponseEntity<String> res1 = restTemplate.exchange("/gateway/orders", HttpMethod.GET, request, String.class);
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res1.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("1");

        // Request 2: OK (remaining: 0)
        ResponseEntity<String> res2 = restTemplate.exchange("/gateway/orders", HttpMethod.GET, request, String.class);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res2.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");

        // Request 3: Exceeded -> HTTP 429
        ResponseEntity<String> res3 = restTemplate.exchange("/gateway/orders", HttpMethod.GET, request, String.class);
        assertThat(res3.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(res3.getHeaders().getFirst("Retry-After")).isNotNull();
        assertThat(res3.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(res3.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(res3.getBody()).contains("Rate limit exceeded");

        // Verify audit log has RATE_LIMITED outcome
        List<GatewayRequestLogEntity> logs = logRepository.findByCorrelationId("corr-rate-limit-test");
        assertThat(logs).isNotEmpty();
        GatewayRequestLogEntity lastLog = logs.get(logs.size() - 1);
        assertThat(lastLog.getStatusCode()).isEqualTo(429);
        assertThat(lastLog.getOutcomeCategory()).isEqualTo("RATE_LIMITED");
    }

    @Test
    @DisplayName("Should maintain independent limits for different clients")
    void shouldMaintainIndependentClientLimits() {
        // Client A has limit 1, Client B has limit 5
        ApiKeyCreatedResponse clientA = apiKeyService.createApiKey(new CreateApiKeyRequest("Client-A", 1));
        ApiKeyCreatedResponse clientB = apiKeyService.createApiKey(new CreateApiKeyRequest("Client-B", 5));

        HttpHeaders headersA = new HttpHeaders();
        headersA.set("X-API-Key", clientA.getApiKey());

        HttpHeaders headersB = new HttpHeaders();
        headersB.set("X-API-Key", clientB.getApiKey());

        // Client A: 1st request OK, 2nd request 429
        ResponseEntity<String> resA1 = restTemplate.exchange("/gateway/orders", HttpMethod.GET, new HttpEntity<>(headersA), String.class);
        assertThat(resA1.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> resA2 = restTemplate.exchange("/gateway/orders", HttpMethod.GET, new HttpEntity<>(headersA), String.class);
        assertThat(resA2.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Client B: should still succeed despite Client A being throttled
        ResponseEntity<String> resB1 = restTemplate.exchange("/gateway/orders", HttpMethod.GET, new HttpEntity<>(headersB), String.class);
        assertThat(resB1.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> resB2 = restTemplate.exchange("/gateway/orders", HttpMethod.GET, new HttpEntity<>(headersB), String.class);
        assertThat(resB2.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Should handle concurrent requests safely without exceeding configured limit")
    void shouldHandleConcurrentRequestsSafely() throws InterruptedException {
        int limit = 5;
        int totalRequests = 15;

        ApiKeyCreatedResponse key = apiKeyService.createApiKey(
                new CreateApiKeyRequest("Concurrent-Client", limit)
        );

        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger status200Count = new AtomicInteger(0);
        AtomicInteger status429Count = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-API-Key", key.getApiKey());

                    ResponseEntity<String> response = restTemplate.exchange(
                            "/gateway/orders",
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            String.class
                    );

                    if (response.getStatusCode() == HttpStatus.OK) {
                        status200Count.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        status429Count.incrementAndGet();
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

        assertThat(status200Count.get()).isEqualTo(limit);
        assertThat(status429Count.get()).isEqualTo(totalRequests - limit);
    }
}
