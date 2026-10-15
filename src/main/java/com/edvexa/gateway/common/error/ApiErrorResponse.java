package com.edvexa.gateway.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<String> details
) {
    public static ApiErrorResponse of(int status, String error, String message, String path, String correlationId) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, correlationId, null);
    }

    public static ApiErrorResponse of(int status, String error, String message, String path, String correlationId, List<String> details) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, correlationId, details);
    }
}
