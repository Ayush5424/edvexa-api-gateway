package com.edvexa.gateway.registry.dto;

import jakarta.validation.constraints.NotNull;

public record ServiceStatusUpdateRequest(
        @NotNull(message = "Enabled flag must not be null")
        Boolean enabled
) {
}
