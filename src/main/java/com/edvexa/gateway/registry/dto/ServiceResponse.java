package com.edvexa.gateway.registry.dto;

import com.edvexa.gateway.registry.ServiceEntity;

import java.time.Instant;

public record ServiceResponse(
        String id,
        String name,
        String baseUrl,
        String pathPrefix,
        int connectionTimeoutMs,
        int responseTimeoutMs,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static ServiceResponse fromEntity(ServiceEntity entity) {
        return new ServiceResponse(
                entity.getId(),
                entity.getName(),
                entity.getBaseUrl(),
                entity.getPathPrefix(),
                entity.getConnectionTimeoutMs(),
                entity.getResponseTimeoutMs(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
