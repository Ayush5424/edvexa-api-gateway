package com.edvexa.gateway.observability;

import com.edvexa.gateway.config.TestRedisConfig;
import com.edvexa.gateway.observability.dto.GatewayMetricsDto;
import com.edvexa.gateway.observability.dto.RecentErrorDto;
import com.edvexa.gateway.observability.dto.ServiceHealthDto;
import com.edvexa.gateway.registry.ServiceEntity;
import com.edvexa.gateway.registry.ServiceRepository;
import com.edvexa.gateway.resilience.CircuitBreakerService;
import com.edvexa.gateway.security.apikey.ApiKeyRepository;
import com.edvexa.gateway.security.apikey.ApiKeyService;
import com.edvexa.gateway.security.apikey.dto.ApiKeyCreatedResponse;
import com.edvexa.gateway.security.apikey.dto.CreateApiKeyRequest;
import com.edvexa.gateway.security.jwt.JwtTokenProvider;
import com.edvexa.gateway.simulator.OrderSimulatorService;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class AdminObservabilityIntegrationTest {

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
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CircuitBreakerService circuitBreakerService;

    @Autowired
    private OrderSimulatorService orderSimulatorService;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String clientApiKey;
    private ServiceEntity orderService;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
        serviceRepository.deleteAll();
        apiKeyRepository.deleteAll();
        orderSimulatorService.reset();
        circuitBreakerService.resetAll();

        adminToken = jwtTokenProvider.generateToken("admin-user", "ROLE_ADMIN");

        ApiKeyCreatedResponse apiKeyResp = apiKeyService.createApiKey(new CreateApiKeyRequest("observability-client", 100));
        clientApiKey = apiKeyResp.getApiKey();

        orderService = new ServiceEntity(
                "srv-order-obs",
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
        logRepository.deleteAll();
        serviceRepository.deleteAll();
        apiKeyRepository.deleteAll();
    }

    @Test
    @DisplayName("Correlation ID is generated, propagated, validated, and returned in response headers")
    void testCorrelationIdLifecycle() {
        // 1. Request with custom valid correlation ID
        HttpHeaders headers1 = new HttpHeaders();
        headers1.set("X-API-Key", clientApiKey);
        headers1.set("X-Correlation-ID", "client-trace-123_45");

        ResponseEntity<String> resp1 = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.GET,
                new HttpEntity<>(headers1),
                String.class
        );

        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp1.getHeaders().getFirst("X-Correlation-ID")).isEqualTo("client-trace-123_45");

        List<GatewayRequestLogEntity> logs1 = logRepository.findByCorrelationId("client-trace-123_45");
        assertThat(logs1).hasSize(1);
        assertThat(logs1.get(0).getCorrelationId()).isEqualTo("client-trace-123_45");

        // 2. Request without correlation ID generates UUID
        HttpHeaders headers2 = new HttpHeaders();
        headers2.set("X-API-Key", clientApiKey);

        ResponseEntity<String> resp2 = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.GET,
                new HttpEntity<>(headers2),
                String.class
        );

        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.OK);
        String generatedCorrId = resp2.getHeaders().getFirst("X-Correlation-ID");
        assertThat(generatedCorrId).isNotNull().matches(Pattern.compile("^[a-f0-9\\-]{36}$"));

        // 3. Request with malformed/unbounded correlation ID is sanitized
        HttpHeaders headers3 = new HttpHeaders();
        headers3.set("X-API-Key", clientApiKey);
        headers3.set("X-Correlation-ID", "<script>bad-id with spaces</script>");

        ResponseEntity<String> resp3 = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.GET,
                new HttpEntity<>(headers3),
                String.class
        );

        assertThat(resp3.getStatusCode()).isEqualTo(HttpStatus.OK);
        String sanitizedId = resp3.getHeaders().getFirst("X-Correlation-ID");
        assertThat(sanitizedId).doesNotContain("<script>").matches(Pattern.compile("^[a-f0-9\\-]{36}$"));
    }

    @Test
    @DisplayName("Administrative observability endpoints strictly enforce ROLE_ADMIN authorization")
    void testAdminAuthorizationEnforcement() {
        // Anonymous request fails with 401
        ResponseEntity<String> anonResp = restTemplate.exchange(
                "/api/v1/admin/metrics",
                HttpMethod.GET,
                null,
                String.class
        );
        assertThat(anonResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Authenticated non-admin JWT (ROLE_CLIENT) fails with 403 Forbidden
        String clientToken = jwtTokenProvider.generateToken("client-app", "ROLE_CLIENT");
        HttpHeaders clientHeaders = new HttpHeaders();
        clientHeaders.setBearerAuth(clientToken);
        ResponseEntity<String> clientResp = restTemplate.exchange(
                "/api/v1/admin/metrics",
                HttpMethod.GET,
                new HttpEntity<>(clientHeaders),
                String.class
        );
        assertThat(clientResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Admin JWT request succeeds with 200 OK
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        ResponseEntity<GatewayMetricsDto> adminResp = restTemplate.exchange(
                "/api/v1/admin/metrics",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                GatewayMetricsDto.class
        );
        assertThat(adminResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Admin metrics endpoint accurately reports counts, outcomes, and latency percentiles")
    void testMetricsAccuracy() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", clientApiKey);

        // Send 3 successful requests
        for (int i = 0; i < 3; i++) {
            restTemplate.exchange("/gateway/orders", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        }

        // Send 1 non-existent route (404 failure)
        restTemplate.exchange("/gateway/unknown-service/xyz", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);

        ResponseEntity<GatewayMetricsDto> metricsResp = restTemplate.exchange(
                "/api/v1/admin/metrics",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                GatewayMetricsDto.class
        );

        assertThat(metricsResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        GatewayMetricsDto metrics = metricsResp.getBody();
        assertThat(metrics).isNotNull();
        assertThat(metrics.totalRequests()).isGreaterThanOrEqualTo(4L);
        assertThat(metrics.successCount()).isGreaterThanOrEqualTo(3L);
        assertThat(metrics.failureCount()).isGreaterThanOrEqualTo(1L);
        assertThat(metrics.clientErrorCount()).isGreaterThanOrEqualTo(1L);
        assertThat(metrics.latency().avgMs()).isGreaterThan(0.0);
        assertThat(metrics.latency().minMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("Service health endpoint accurately exposes circuit-breaker states and status")
    void testServiceHealthEndpoint() {
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);

        // 1. Initial healthy state
        ResponseEntity<List<ServiceHealthDto>> healthResp1 = restTemplate.exchange(
                "/api/v1/admin/services/health",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(healthResp1.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ServiceHealthDto> list1 = healthResp1.getBody();
        assertThat(list1).isNotEmpty();
        ServiceHealthDto dto = list1.stream()
                .filter(s -> s.serviceId().equals(orderService.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(dto.circuitBreakerState()).isEqualTo("CLOSED");
        assertThat(dto.status()).isEqualTo("HEALTHY");

        // 2. Transition circuit breaker to OPEN
        for (int i = 0; i < 5; i++) {
            circuitBreakerService.recordFailure(orderService.getId(), 50L, new RuntimeException("Forced err"));
        }

        ResponseEntity<List<ServiceHealthDto>> healthResp2 = restTemplate.exchange(
                "/api/v1/admin/services/health",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                new ParameterizedTypeReference<>() {}
        );

        List<ServiceHealthDto> list2 = healthResp2.getBody();
        ServiceHealthDto openDto = list2.stream()
                .filter(s -> s.serviceId().equals(orderService.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(openDto.circuitBreakerState()).isEqualTo("OPEN");
        assertThat(openDto.status()).isEqualTo("UNHEALTHY");
    }

    @Test
    @DisplayName("Paginated audit logs and recent errors sanitize sensitive headers and bodies")
    void testAuditLogsAndErrorsPaginationAndRedaction() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", clientApiKey);
        headers.set("Authorization", "Bearer super-secret-jwt-token-12345");

        // Trigger a 404 client error
        restTemplate.exchange("/gateway/invalid-route", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);

        // Query paginated errors
        ResponseEntity<Map<String, Object>> errorResp = restTemplate.exchange(
                "/api/v1/admin/audit/errors?page=0&size=10",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(errorResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> pageMap = errorResp.getBody();
        assertThat(pageMap).containsKey("content");

        String pageJson = objectMapper.valueToTree(pageMap).toString();
        // Crucial security verification: raw secrets, auth headers, and keys are NEVER present in audit records
        assertThat(pageJson).doesNotContain("super-secret-jwt-token-12345");
        assertThat(pageJson).doesNotContain(clientApiKey);

        // Query by correlation ID
        List<GatewayRequestLogEntity> logs = logRepository.findAll();
        assertThat(logs).isNotEmpty();
        String targetCorrId = logs.get(0).getCorrelationId();

        ResponseEntity<List<GatewayRequestLogEntity>> corrResp = restTemplate.exchange(
                "/api/v1/admin/audit/correlation/" + targetCorrId,
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(corrResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(corrResp.getBody()).isNotEmpty();
        assertThat(corrResp.getBody().get(0).getCorrelationId()).isEqualTo(targetCorrId);
    }
}
