package com.edvexa.gateway.security.apikey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "api_keys")
public class ApiKeyEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "client_name", length = 128, nullable = false)
    private String clientName;

    @Column(name = "key_hash", length = 128, nullable = false, unique = true)
    private String keyHash;

    @Column(name = "key_prefix", length = 16, nullable = false)
    private String keyPrefix;

    @Column(name = "rate_limit_per_minute", nullable = false)
    private int rateLimitPerMinute = 60;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private ApiKeyStatus status = ApiKeyStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public ApiKeyEntity() {
    }

    public ApiKeyEntity(String id, String clientName, String keyHash, String keyPrefix, int rateLimitPerMinute) {
        this.id = id;
        this.clientName = clientName;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.rateLimitPerMinute = rateLimitPerMinute;
        this.status = ApiKeyStatus.ACTIVE;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public void revoke() {
        this.status = ApiKeyStatus.REVOKED;
        this.revokedAt = Instant.now();
    }

    public boolean isActive() {
        return this.status == ApiKeyStatus.ACTIVE;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public ApiKeyStatus getStatus() {
        return status;
    }

    public void setStatus(ApiKeyStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
