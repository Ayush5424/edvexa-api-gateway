package com.edvexa.gateway.security.apikey.dto;

import com.edvexa.gateway.security.apikey.ApiKeyEntity;
import com.edvexa.gateway.security.apikey.ApiKeyStatus;

import java.time.Instant;

public class ApiKeyResponse {

    private String id;
    private String clientName;
    private String keyPrefix;
    private int rateLimitPerMinute;
    private ApiKeyStatus status;
    private Instant createdAt;
    private Instant revokedAt;

    public ApiKeyResponse() {
    }

    public ApiKeyResponse(String id, String clientName, String keyPrefix, int rateLimitPerMinute,
                          ApiKeyStatus status, Instant createdAt, Instant revokedAt) {
        this.id = id;
        this.clientName = clientName;
        this.keyPrefix = keyPrefix;
        this.rateLimitPerMinute = rateLimitPerMinute;
        this.status = status;
        this.createdAt = createdAt;
        this.revokedAt = revokedAt;
    }

    public static ApiKeyResponse fromEntity(ApiKeyEntity entity) {
        return new ApiKeyResponse(
                entity.getId(),
                entity.getClientName(),
                entity.getKeyPrefix(),
                entity.getRateLimitPerMinute(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getRevokedAt()
        );
    }

    public String getId() {
        return id;
    }

    public String getClientName() {
        return clientName;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public ApiKeyStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
