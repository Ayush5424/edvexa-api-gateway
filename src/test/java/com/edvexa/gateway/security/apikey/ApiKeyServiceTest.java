package com.edvexa.gateway.security.apikey;

import com.edvexa.gateway.common.error.ResourceNotFoundException;
import com.edvexa.gateway.security.apikey.dto.ApiKeyCreatedResponse;
import com.edvexa.gateway.security.apikey.dto.ApiKeyResponse;
import com.edvexa.gateway.security.apikey.dto.CreateApiKeyRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyRepository);
    }

    @Test
    @DisplayName("Should create API key with prefix, secret, and hashed persistence")
    void shouldCreateApiKey() {
        CreateApiKeyRequest request = new CreateApiKeyRequest("Billing Service", 100);

        when(apiKeyRepository.save(any(ApiKeyEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiKeyCreatedResponse response = apiKeyService.createApiKey(request);

        assertThat(response.getId()).startsWith("key_");
        assertThat(response.getClientName()).isEqualTo("Billing Service");
        assertThat(response.getApiKey()).startsWith("edx_");
        assertThat(response.getKeyPrefix()).startsWith("edx_");
        assertThat(response.getRateLimitPerMinute()).isEqualTo(100);
        assertThat(response.getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);

        ArgumentCaptor<ApiKeyEntity> captor = ArgumentCaptor.forClass(ApiKeyEntity.class);
        verify(apiKeyRepository).save(captor.capture());

        ApiKeyEntity saved = captor.getValue();
        assertThat(saved.getKeyHash()).isEqualTo(apiKeyService.hashKey(response.getApiKey()));
        assertThat(saved.getKeyPrefix()).isEqualTo(response.getKeyPrefix());
    }

    @Test
    @DisplayName("Should successfully validate an active API key")
    void shouldValidateActiveApiKey() {
        String rawKey = "edx_1234abcd_secretkey1234567890abcdef123456";
        String hash = apiKeyService.hashKey(rawKey);

        ApiKeyEntity activeEntity = new ApiKeyEntity("key_1", "Analytics", hash, "edx_1234abcd", 50);

        when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.of(activeEntity));

        Optional<ApiKeyEntity> result = apiKeyService.validateApiKey(rawKey);

        assertThat(result).isPresent();
        assertThat(result.get().getClientName()).isEqualTo("Analytics");
    }

    @Test
    @DisplayName("Should reject validation when API key is revoked")
    void shouldRejectRevokedApiKey() {
        String rawKey = "edx_1234abcd_secretkey1234567890abcdef123456";
        String hash = apiKeyService.hashKey(rawKey);

        ApiKeyEntity revokedEntity = new ApiKeyEntity("key_1", "Analytics", hash, "edx_1234abcd", 50);
        revokedEntity.revoke();

        when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.of(revokedEntity));

        Optional<ApiKeyEntity> result = apiKeyService.validateApiKey(rawKey);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should reject validation when API key is null or unknown")
    void shouldRejectUnknownApiKey() {
        assertThat(apiKeyService.validateApiKey(null)).isEmpty();
        assertThat(apiKeyService.validateApiKey("   ")).isEmpty();

        String unknownKey = "edx_unknown_secret";
        when(apiKeyRepository.findByKeyHash(apiKeyService.hashKey(unknownKey))).thenReturn(Optional.empty());

        assertThat(apiKeyService.validateApiKey(unknownKey)).isEmpty();
    }

    @Test
    @DisplayName("Should revoke existing API key")
    void shouldRevokeApiKey() {
        ApiKeyEntity entity = new ApiKeyEntity("key_123", "Payment Gateway", "somehash", "edx_pay1", 100);

        when(apiKeyRepository.findById("key_123")).thenReturn(Optional.of(entity));
        when(apiKeyRepository.save(any(ApiKeyEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiKeyResponse response = apiKeyService.revokeApiKey("key_123");

        assertThat(response.getStatus()).isEqualTo(ApiKeyStatus.REVOKED);
        assertThat(response.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when revoking non-existent key")
    void shouldThrowWhenRevokingNonExistentKey() {
        when(apiKeyRepository.findById("non-existent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.revokeApiKey("non-existent"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("non-existent");
    }

    @Test
    @DisplayName("Should list all API keys in descending order")
    void shouldListApiKeys() {
        ApiKeyEntity key1 = new ApiKeyEntity("key_1", "Client A", "hash1", "edx_a1b2", 60);
        ApiKeyEntity key2 = new ApiKeyEntity("key_2", "Client B", "hash2", "edx_c3d4", 120);

        when(apiKeyRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(key1, key2));

        List<ApiKeyResponse> list = apiKeyService.listApiKeys();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getClientName()).isEqualTo("Client A");
        assertThat(list.get(1).getClientName()).isEqualTo("Client B");
    }
}
