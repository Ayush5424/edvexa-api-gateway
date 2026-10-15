package com.edvexa.gateway.registry.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record ServiceUpdateRequest(
        @Pattern(regexp = "^[a-zA-Z0-9_-]{2,64}$", message = "Service name must be 2-64 alphanumeric characters, underscores, or hyphens")
        String name,

        String baseUrl,

        @Pattern(regexp = "^/[a-zA-Z0-9_/-]{1,64}$", message = "Path prefix must start with '/' and contain 1-64 valid path characters")
        String pathPrefix,

        @Min(value = 100, message = "Connection timeout must be at least 100ms")
        @Max(value = 60000, message = "Connection timeout cannot exceed 60000ms")
        Integer connectionTimeoutMs,

        @Min(value = 100, message = "Response timeout must be at least 100ms")
        @Max(value = 300000, message = "Response timeout cannot exceed 300000ms")
        Integer responseTimeoutMs,

        Boolean enabled
) {
}
