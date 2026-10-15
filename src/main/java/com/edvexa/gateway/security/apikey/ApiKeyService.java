package com.edvexa.gateway.security.apikey;

import com.edvexa.gateway.common.error.ResourceNotFoundException;
import com.edvexa.gateway.security.apikey.dto.ApiKeyCreatedResponse;
import com.edvexa.gateway.security.apikey.dto.ApiKeyResponse;
import com.edvexa.gateway.security.apikey.dto.CreateApiKeyRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String KEY_PREFIX_CONSTANT = "edx_";

    private final ApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Transactional
    public ApiKeyCreatedResponse createApiKey(CreateApiKeyRequest request) {
        String id = "key_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        byte[] prefixBytes = new byte[4];
        secureRandom.nextBytes(prefixBytes);
        String randomHex = HexFormat.of().formatHex(prefixBytes);
        String fullPrefix = KEY_PREFIX_CONSTANT + randomHex;

        byte[] secretBytes = new byte[16];
        secureRandom.nextBytes(secretBytes);
        String secretPart = HexFormat.of().formatHex(secretBytes);

        String rawApiKey = fullPrefix + "_" + secretPart;
        String keyHash = hashKey(rawApiKey);

        ApiKeyEntity entity = new ApiKeyEntity(
                id,
                request.getClientName(),
                keyHash,
                fullPrefix,
                request.getRateLimitPerMinute()
        );

        ApiKeyEntity saved = apiKeyRepository.save(entity);
        log.info("Created new API key id={} clientName={} prefix={}", saved.getId(), saved.getClientName(), saved.getKeyPrefix());

        return new ApiKeyCreatedResponse(
                saved.getId(),
                saved.getClientName(),
                rawApiKey,
                saved.getKeyPrefix(),
                saved.getRateLimitPerMinute(),
                saved.getStatus(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys() {
        return apiKeyRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(ApiKeyResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApiKeyResponse getApiKey(String id) {
        ApiKeyEntity entity = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found with id: " + id));
        return ApiKeyResponse.fromEntity(entity);
    }

    @Transactional
    public ApiKeyResponse revokeApiKey(String id) {
        ApiKeyEntity entity = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found with id: " + id));

        entity.revoke();
        ApiKeyEntity updated = apiKeyRepository.save(entity);
        log.info("Revoked API key id={} clientName={}", updated.getId(), updated.getClientName());
        return ApiKeyResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public Optional<ApiKeyEntity> validateApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Optional.empty();
        }

        String keyHash = hashKey(rawApiKey.trim());
        return apiKeyRepository.findByKeyHash(keyHash)
                .filter(ApiKeyEntity::isActive);
    }

    public String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
