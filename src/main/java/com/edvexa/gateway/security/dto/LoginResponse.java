package com.edvexa.gateway.security.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInMs,
        String username,
        String role
) {
    public static LoginResponse of(String token, long expiresInMs, String username, String role) {
        return new LoginResponse(token, "Bearer", expiresInMs, username, role);
    }
}
