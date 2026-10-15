package com.edvexa.gateway.security.dto;

public record AdminBootstrapResponse(
        String message,
        String username,
        String role
) {
}
