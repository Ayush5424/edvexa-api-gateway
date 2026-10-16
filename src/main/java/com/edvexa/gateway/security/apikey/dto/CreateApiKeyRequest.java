package com.edvexa.gateway.security.apikey.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateApiKeyRequest {

    @NotBlank(message = "Client name cannot be blank")
    @Size(max = 128, message = "Client name cannot exceed 128 characters")
    private String clientName;

    @Min(value = 1, message = "Rate limit must be at least 1 request per minute")
    @Max(value = 100000, message = "Rate limit cannot exceed 100,000 requests per minute")
    private int rateLimitPerMinute = 60;

    public CreateApiKeyRequest() {
    }

    public CreateApiKeyRequest(String clientName, int rateLimitPerMinute) {
        this.clientName = clientName;
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }
}
