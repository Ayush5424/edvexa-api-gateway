package com.edvexa.gateway.security.apikey.dto;

import com.edvexa.gateway.security.apikey.ApiKeyStatus;

import java.time.Instant;

public class ApiKeyCreatedResponse {

    private String id;
    private String clientName;
    private String apiKey;
    private String keyPrefix;
    private int rateLimitPerMinute;
    private ApiKeyStatus status;
    private Instant createdAt;

    public ApiKeyCreatedResponse() {
    }

    public ApiKeyCreatedResponse(String id, String clientName, String apiKey, String keyPrefix,
                                 int rateLimitPerMinute, ApiKeyStatus status, Instant createdAt) {
        this.id = id;
        this.clientName = clientName;
        this.apiKey = apiKey;
        this.keyPrefix = keyPrefix;
        this.rateLimitPerMinute = rateLimitPerMinute;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getClientName() {
        return clientName;
    }

    public String getApiKey() {
        return apiKey;
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
}
