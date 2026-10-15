package com.edvexa.gateway.registry.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ServiceRegistrationRequest(
        @NotBlank(message = "Service name is required")
        @Pattern(regexp = "^[a-zA-Z0-9_-]{2,64}$", message = "Service name must be 2-64 alphanumeric characters, underscores, or hyphens")
        String name,

        @NotBlank(message = "Base URL is required")
        String baseUrl,

        @NotBlank(message = "Path prefix is required")
        @Pattern(regexp = "^/[a-zA-Z0-9_/-]{1,64}$", message = "Path prefix must start with '/' and contain 1-64 valid path characters")
        String pathPrefix,

        @Min(value = 100, message = "Connection timeout must be at least 100ms")
        @Max(value = 60000, message = "Connection timeout cannot exceed 60000ms")
        int connectionTimeoutMs,

        @Min(value = 100, message = "Response timeout must be at least 100ms")
        @Max(value = 300000, message = "Response timeout cannot exceed 300000ms")
        int responseTimeoutMs,

        boolean enabled
) {
    public ServiceRegistrationRequest {
        if (connectionTimeoutMs == 0) {
            connectionTimeoutMs = 5000;
        }
        if (responseTimeoutMs == 0) {
            responseTimeoutMs = 10000;
        }
    }
}
