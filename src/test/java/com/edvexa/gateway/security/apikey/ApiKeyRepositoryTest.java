package com.edvexa.gateway.security.apikey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ApiKeyRepositoryTest {

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Test
    @DisplayName("Should persist and retrieve API key by key hash and prefix")
    void shouldPersistAndRetrieveApiKey() {
        ApiKeyEntity key = new ApiKeyEntity(
                "key-test-uuid-1",
                "Mobile App Client",
                "sha256hash1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
                "edx_live_a1b2",
                120
        );

        apiKeyRepository.save(key);

        Optional<ApiKeyEntity> foundByHash = apiKeyRepository.findByKeyHash(key.getKeyHash());
        assertThat(foundByHash).isPresent();
        assertThat(foundByHash.get().getClientName()).isEqualTo("Mobile App Client");
        assertThat(foundByHash.get().getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
        assertThat(foundByHash.get().getRateLimitPerMinute()).isEqualTo(120);
        assertThat(foundByHash.get().getCreatedAt()).isNotNull();

        List<ApiKeyEntity> foundByPrefix = apiKeyRepository.findByKeyPrefix("edx_live_a1b2");
        assertThat(foundByPrefix).hasSize(1);
        assertThat(foundByPrefix.get(0).getId()).isEqualTo("key-test-uuid-1");
    }

    @Test
    @DisplayName("Should update status and revokedAt timestamp when key is revoked")
    void shouldRevokeApiKey() {
        ApiKeyEntity key = new ApiKeyEntity(
                "key-test-uuid-2",
                "Web Frontend",
                "sha256hashWebClientabcdef1234567890abcdef1234567890abcdef1234567890",
                "edx_live_web1",
                60
        );

        apiKeyRepository.save(key);
        assertThat(key.isActive()).isTrue();

        key.revoke();
        apiKeyRepository.save(key);

        ApiKeyEntity updated = apiKeyRepository.findById("key-test-uuid-2").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ApiKeyStatus.REVOKED);
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getRevokedAt()).isNotNull();
    }
}
