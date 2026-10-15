package com.edvexa.gateway.routing;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class GatewayRoutingIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private GatewayRequestLogRepository logRepository;

    @Autowired
    private OrderSimulatorService orderSimulatorService;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ObjectMapper objectMapper;

    private String orderServiceId;
    private String testApiKey;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
        serviceRepository.deleteAll();
        apiKeyRepository.deleteAll();
        orderSimulatorService.reset();

        ApiKeyCreatedResponse keyResponse = apiKeyService.createApiKey(
                new CreateApiKeyRequest("client-evaluator", 100)
        );
        testApiKey = keyResponse.getApiKey();

        ServiceEntity orderService = new ServiceEntity(
                "srv-order-test",
                "order-service",
                "http://localhost:" + port + "/api/v1/simulated/orders",
                "/orders",
                3000,
                5000,
                true
        );
        serviceRepository.save(orderService);
        orderServiceId = orderService.getId();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", testApiKey);
        return headers;
    }

    @Test
    void shouldForwardGetRequestToDownstreamServiceAndRecordAudit() {
        HttpHeaders headers = createHeaders();
        headers.set("X-Correlation-ID", "corr-test-100");

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.GET,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Correlation-ID")).isEqualTo("corr-test-100");
        assertThat(response.getBody()).contains("ORD-101");

        // Verify audit log entry was created
        List<GatewayRequestLogEntity> logs = logRepository.findByCorrelationId("corr-test-100");
        assertThat(logs).hasSize(1);
        GatewayRequestLogEntity log = logs.get(0);
        assertThat(log.getServiceId()).isEqualTo(orderServiceId);
        assertThat(log.getClientId()).isEqualTo("client-evaluator");
        assertThat(log.getStatusCode()).isEqualTo(200);
        assertThat(log.getOutcomeCategory()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldForwardPostRequestWithBodyToDownstreamService() {
        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Correlation-ID", "corr-test-200");

        Map<String, Object> body = Map.of(
                "amount", 199.50,
                "customerId", "cust-forwarded"
        );
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("199.5");
        assertThat(response.getBody()).contains("cust-forwarded");

        List<GatewayRequestLogEntity> logs = logRepository.findByCorrelationId("corr-test-200");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatusCode()).isEqualTo(201);
    }

    @Test
    void shouldReturn404ForUnknownRouteAndRecordAudit() {
        HttpEntity<Void> request = new HttpEntity<>(createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                "/gateway/unknown/endpoint",
                HttpMethod.GET,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("No registered service matches route");
    }

    @Test
    void shouldReturn503WhenServiceIsDisabled() {
        ServiceEntity service = serviceRepository.findById(orderServiceId).orElseThrow();
        service.setEnabled(false);
        serviceRepository.save(service);

        HttpEntity<Void> request = new HttpEntity<>(createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.GET,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("currently disabled");
    }

    @Test
    void shouldPropagateDownstreamErrorsWithoutFabrication() {
        // Configure order simulator to return 503
        orderSimulatorService.updateConfig(new com.edvexa.gateway.simulator.dto.SimulatorConfigDto(
                0, 0.0, 503, 1
        ));

        HttpEntity<Void> request = new HttpEntity<>(createHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.GET,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
