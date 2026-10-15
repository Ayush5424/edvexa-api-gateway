package com.edvexa.gateway.security.apikey;

import com.edvexa.gateway.config.TestRedisConfig;
import com.edvexa.gateway.observability.GatewayRequestLogEntity;
import com.edvexa.gateway.observability.GatewayRequestLogRepository;
import com.edvexa.gateway.registry.ServiceEntity;
import com.edvexa.gateway.registry.ServiceRepository;
import com.edvexa.gateway.security.dto.LoginRequest;
import com.edvexa.gateway.security.dto.LoginResponse;
import com.edvexa.gateway.security.apikey.dto.ApiKeyCreatedResponse;
import com.edvexa.gateway.security.apikey.dto.ApiKeyResponse;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class ApiKeyAuthenticationIntegrationTest {

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

    private String validApiKey;
    private String adminJwtToken;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
        serviceRepository.deleteAll();
        apiKeyRepository.deleteAll();
        orderSimulatorService.reset();

        // Register downstream order service
        ServiceEntity orderService = new ServiceEntity(
                "srv-order-auth-test",
                "order-auth-service",
                "http://localhost:" + port + "/api/v1/simulated/orders",
                "/orders",
                3000,
                5000,
                true
        );
        serviceRepository.save(orderService);

        // Create an active API key
        ApiKeyCreatedResponse keyResponse = apiKeyService.createApiKey(
                new CreateApiKeyRequest("Partner-Mobile-App", 120)
        );
        validApiKey = keyResponse.getApiKey();

        // Login as default admin to obtain admin JWT
        LoginRequest loginRequest = new LoginRequest("admin", "AdminPass123!");
        ResponseEntity<LoginResponse> loginResponse = restTemplate.postForEntity(
                "/api/v1/auth/login",
                loginRequest,
                LoginResponse.class
        );
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        adminJwtToken = loginResponse.getBody().token();
    }

    @Test
    @DisplayName("Should reject gateway request when X-API-Key header is missing")
    void shouldRejectGatewayRequestWhenApiKeyIsMissing() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/gateway/orders",
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Missing API key in X-API-Key header");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("Should reject gateway request when X-API-Key is invalid")
    void shouldRejectGatewayRequestWhenApiKeyIsInvalid() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "edx_invalid_fake_key_12345");

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.GET,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid or revoked API key");
    }

    @Test
    @DisplayName("Should reject gateway request when API key has been revoked")
    void shouldRejectGatewayRequestWhenApiKeyIsRevoked() {
        // Create and immediately revoke key
        ApiKeyCreatedResponse created = apiKeyService.createApiKey(
                new CreateApiKeyRequest("Revoked-Client", 60)
        );
        apiKeyService.revokeApiKey(created.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", created.getApiKey());

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.GET,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid or revoked API key");
    }

    @Test
    @DisplayName("Should allow gateway request when valid API key is supplied and log client identity")
    void shouldAllowGatewayRequestWithValidApiKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", validApiKey);
        headers.set("X-Correlation-ID", "corr-api-key-test-1");

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/gateway/orders",
                HttpMethod.GET,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("ORD-101");

        List<GatewayRequestLogEntity> logs = logRepository.findByCorrelationId("corr-api-key-test-1");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getClientId()).isEqualTo("Partner-Mobile-App");
    }

    @Test
    @DisplayName("Should deny unauthenticated requests to API key administration endpoints")
    void shouldDenyUnauthenticatedAccessToApiKeyAdminEndpoints() {
        CreateApiKeyRequest request = new CreateApiKeyRequest("Hacker Client", 50);

        ResponseEntity<String> postResponse = restTemplate.postForEntity(
                "/api/v1/api-keys",
                request,
                String.class
        );
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> getResponse = restTemplate.getForEntity(
                "/api/v1/api-keys",
                String.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should allow administrator to create, list, and revoke API keys")
    void shouldAllowAdminToManageApiKeys() {
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminJwtToken);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);

        // 1. Create API key via admin endpoint
        CreateApiKeyRequest createRequest = new CreateApiKeyRequest("Warehouse Service", 300);
        HttpEntity<CreateApiKeyRequest> postRequest = new HttpEntity<>(createRequest, adminHeaders);

        ResponseEntity<ApiKeyCreatedResponse> createResponse = restTemplate.exchange(
                "/api/v1/api-keys",
                HttpMethod.POST,
                postRequest,
                ApiKeyCreatedResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ApiKeyCreatedResponse createdBody = createResponse.getBody();
        assertThat(createdBody).isNotNull();
        assertThat(createdBody.getClientName()).isEqualTo("Warehouse Service");
        assertThat(createdBody.getApiKey()).isNotBlank();
        assertThat(createdBody.getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);

        // 2. List API keys
        HttpEntity<Void> listRequest = new HttpEntity<>(adminHeaders);
        ResponseEntity<List<ApiKeyResponse>> listResponse = restTemplate.exchange(
                "/api/v1/api-keys",
                HttpMethod.GET,
                listRequest,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ApiKeyResponse> keys = listResponse.getBody();
        assertThat(keys).isNotNull();
        assertThat(keys.stream().anyMatch(k -> k.getId().equals(createdBody.getId()))).isTrue();

        // 3. Revoke API key
        ResponseEntity<ApiKeyResponse> revokeResponse = restTemplate.exchange(
                "/api/v1/api-keys/" + createdBody.getId(),
                HttpMethod.DELETE,
                listRequest,
                ApiKeyResponse.class
        );

        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(revokeResponse.getBody()).isNotNull();
        assertThat(revokeResponse.getBody().getStatus()).isEqualTo(ApiKeyStatus.REVOKED);
        assertThat(revokeResponse.getBody().getRevokedAt()).isNotNull();
    }
}
