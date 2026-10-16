package com.edvexa.gateway.idempotency;

import com.edvexa.gateway.config.TestRedisConfig;
import com.edvexa.gateway.observability.GatewayRequestLogRepository;
import com.edvexa.gateway.registry.ServiceEntity;
import com.edvexa.gateway.registry.ServiceRepository;
import com.edvexa.gateway.resilience.CircuitBreakerService;
import com.edvexa.gateway.security.apikey.ApiKeyRepository;
import com.edvexa.gateway.security.apikey.ApiKeyService;
import com.edvexa.gateway.security.apikey.dto.ApiKeyCreatedResponse;
import com.edvexa.gateway.security.apikey.dto.CreateApiKeyRequest;
import com.edvexa.gateway.simulator.OrderSimulatorService;
import com.edvexa.gateway.simulator.dto.SimulatorConfigDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class IdempotencyIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private GatewayRequestLogRepository logRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private OrderSimulatorService orderSimulatorService;

    @Autowired
    private CircuitBreakerService circuitBreakerService;

    @Autowired
    private ObjectMapper objectMapper;

    private String clientKey1;
    private String clientKey2;
    private String clientId1;
    private String clientId2;

    @BeforeEach
    void setUp() {
        idempotencyRecordRepository.deleteAll();
        logRepository.deleteAll();
        serviceRepository.deleteAll();
        apiKeyRepository.deleteAll();
        orderSimulatorService.reset();
        circuitBreakerService.resetAll();

        ApiKeyCreatedResponse keyResp1 = apiKeyService.createApiKey(new CreateApiKeyRequest("client-alpha", 100));
        clientKey1 = keyResp1.getApiKey();
        clientId1 = keyResp1.getClientName();

        ApiKeyCreatedResponse keyResp2 = apiKeyService.createApiKey(new CreateApiKeyRequest("client-beta", 100));
        clientKey2 = keyResp2.getApiKey();
        clientId2 = keyResp2.getClientName();

        ServiceEntity orderService = new ServiceEntity(
                "srv-order-idem-test",
                "order-service",
                "http://localhost:" + port + "/api/v1/simulated/orders",
                "/orders",
                3000,
                5000,
                true
        );
        serviceRepository.save(orderService);
    }

    @AfterEach
    void tearDown() {
        orderSimulatorService.reset();
        circuitBreakerService.resetAll();
        idempotencyRecordRepository.deleteAll();
        logRepository.deleteAll();
        serviceRepository.deleteAll();
        apiKeyRepository.deleteAll();
    }

    @Test
    @DisplayName("First execution of write request with Idempotency-Key succeeds and persists completed record")
    void testFirstExecution_persistsCompletedRecord() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", clientKey1);
        headers.set("Idempotency-Key", "idemp-key-001");
        headers.setContentType(MediaType.APPLICATION_JSON);

        String payload = objectMapper.writeValueAsString(Map.of("amount", 199.99, "customerId", "cust-99"));
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> bodyMap = objectMapper.readValue(response.getBody(), new TypeReference<>() {});
        assertThat(bodyMap).containsKey("id");
        assertThat(bodyMap.get("status")).isEqualTo("CREATED");

        // Verify database record
        Optional<IdempotencyRecordEntity> recordOpt = idempotencyRecordRepository.findByClientIdAndIdempotencyKey(
                clientId1, "idemp-key-001"
        );
        assertThat(recordOpt).isPresent();
        IdempotencyRecordEntity record = recordOpt.get();
        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(record.getResponseCode()).isEqualTo(201);
        assertThat(record.getResponseBody()).isNotNull();
    }

    @Test
    @DisplayName("Duplicate request with same Idempotency-Key replays cached response without invoking downstream")
    void testResponseReplay_doesNotCallDownstream() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", clientKey1);
        headers.set("Idempotency-Key", "idemp-key-replay");
        headers.setContentType(MediaType.APPLICATION_JSON);

        String payload = objectMapper.writeValueAsString(Map.of("amount", 349.50, "customerId", "cust-replay"));
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);

        // First call
        ResponseEntity<String> response1 = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.POST,
                entity,
                String.class
        );
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        int initialOrderCount = orderSimulatorService.listOrders().size();

        // Second call with identical payload and key
        ResponseEntity<String> response2 = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.POST,
                entity,
                String.class
        );

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response2.getHeaders().getFirst("X-Cache-Lookup")).isEqualTo("HIT-IDEMPOTENT");
        assertThat(response2.getBody()).isEqualTo(response1.getBody());

        // Ensure downstream order count did not increase
        int subsequentOrderCount = orderSimulatorService.listOrders().size();
        assertThat(subsequentOrderCount).isEqualTo(initialOrderCount);
    }

    @Test
    @DisplayName("Reuse of Idempotency-Key with different payload triggers HTTP 409 Conflict")
    void testPayloadMismatch_returns409Conflict() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", clientKey1);
        headers.set("Idempotency-Key", "idemp-key-mismatch");
        headers.setContentType(MediaType.APPLICATION_JSON);

        String payload1 = objectMapper.writeValueAsString(Map.of("amount", 100.00, "customerId", "cust-1"));
        String payload2 = objectMapper.writeValueAsString(Map.of("amount", 200.00, "customerId", "cust-2"));

        // First call
        ResponseEntity<String> response1 = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.POST,
                new HttpEntity<>(payload1, headers),
                String.class
        );
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second call with different payload
        ResponseEntity<String> response2 = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.POST,
                new HttpEntity<>(payload2, headers),
                String.class
        );

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response2.getBody()).contains("previously used with a different request payload or endpoint");
    }

    @Test
    @DisplayName("Independent clients using identical Idempotency-Key succeed independently without conflict")
    void testIndependentClients_sameKey_succeedIndependently() throws Exception {
        String sharedKey = "shared-idempotency-key-01";
        String payload = objectMapper.writeValueAsString(Map.of("amount", 88.00, "customerId", "cust-shared"));

        // Client 1
        HttpHeaders headers1 = new HttpHeaders();
        headers1.set("X-API-Key", clientKey1);
        headers1.set("Idempotency-Key", sharedKey);
        headers1.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response1 = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers1),
                String.class
        );
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Client 2 with same key
        HttpHeaders headers2 = new HttpHeaders();
        headers2.set("X-API-Key", clientKey2);
        headers2.set("Idempotency-Key", sharedKey);
        headers2.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response2 = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers2),
                String.class
        );
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify two distinct records in database
        Optional<IdempotencyRecordEntity> rec1 = idempotencyRecordRepository.findByClientIdAndIdempotencyKey(clientId1, sharedKey);
        Optional<IdempotencyRecordEntity> rec2 = idempotencyRecordRepository.findByClientIdAndIdempotencyKey(clientId2, sharedKey);

        assertThat(rec1).isPresent();
        assertThat(rec2).isPresent();
        assertThat(rec1.get().getId()).isNotEqualTo(rec2.get().getId());
    }

    @Test
    @DisplayName("Downstream 5xx failure aborts idempotency record and allows client retry with same key")
    void testDownstreamFailure_abortsRecordAndAllowsRetry() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", clientKey1);
        headers.set("Idempotency-Key", "idemp-key-fail-retry");
        headers.setContentType(MediaType.APPLICATION_JSON);

        String payload = objectMapper.writeValueAsString(Map.of("amount", 75.00, "customerId", "cust-fail"));
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);

        // Configure simulator to fail with 500
        orderSimulatorService.updateConfig(new SimulatorConfigDto(0, 1.0, 500, null));

        ResponseEntity<String> failResponse = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.POST,
                entity,
                String.class
        );
        assertThat(failResponse.getStatusCode().is5xxServerError()).isTrue();

        // Verify record is NOT stuck in IN_PROGRESS
        Optional<IdempotencyRecordEntity> recordAfterFailure = idempotencyRecordRepository.findByClientIdAndIdempotencyKey(
                clientId1, "idemp-key-fail-retry"
        );
        assertThat(recordAfterFailure).isEmpty();

        // Restore simulator to healthy
        orderSimulatorService.reset();

        // Retry same request with same key
        ResponseEntity<String> successResponse = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.POST,
                entity,
                String.class
        );
        assertThat(successResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Now record should be COMPLETED
        Optional<IdempotencyRecordEntity> recordAfterRetry = idempotencyRecordRepository.findByClientIdAndIdempotencyKey(
                clientId1, "idemp-key-fail-retry"
        );
        assertThat(recordAfterRetry).isPresent();
        assertThat(recordAfterRetry.get().getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
    }

    @Test
    @DisplayName("Concurrent duplicate requests with same key safely prevent duplicate execution")
    void testConcurrentRequests_sameKey_preventsDuplicateExecution() throws Exception {
        int threadCount = 4;
        String sharedKey = "concurrent-key-999";
        String payload = objectMapper.writeValueAsString(Map.of("amount", 500.00, "customerId", "cust-concurrent"));

        // Add 200ms delay to downstream to increase window of concurrency
        orderSimulatorService.updateConfig(new SimulatorConfigDto(200, 0.0, null, null));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-API-Key", clientKey1);
                headers.set("Idempotency-Key", sharedKey);
                headers.setContentType(MediaType.APPLICATION_JSON);

                return restTemplate.exchange(
                        "/gateway/orders",
                        HttpMethod.POST,
                        new HttpEntity<>(payload, headers),
                        String.class
                );
            }));
        }

        // Release all threads simultaneously
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        List<Integer> statusCodes = new ArrayList<>();
        for (Future<ResponseEntity<String>> future : futures) {
            statusCodes.add(future.get().getStatusCode().value());
        }

        // At least one request must succeed (201 or replayed 201), and any concurrent collision must get 409 Conflict
        assertThat(statusCodes).contains(201);
        long conflictCount = statusCodes.stream().filter(c -> c == 409).count();
        long successCount = statusCodes.stream().filter(c -> c == 201).count();

        assertThat(successCount + conflictCount).isEqualTo(threadCount);
        // Ensure final database state is single completed record
        Optional<IdempotencyRecordEntity> finalRecord = idempotencyRecordRepository.findByClientIdAndIdempotencyKey(
                clientId1, sharedKey
        );
        assertThat(finalRecord).isPresent();
        assertThat(finalRecord.get().getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
    }
}
