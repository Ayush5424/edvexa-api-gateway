package com.edvexa.gateway.simulator.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SimulatorConfigDto(
        @Min(value = 0, message = "Delay must be at least 0 ms")
        @Max(value = 60000, message = "Delay cannot exceed 60000 ms")
        Integer delayMs,

        @DecimalMin(value = "0.0", message = "Failure rate cannot be negative")
        @DecimalMax(value = "1.0", message = "Failure rate cannot exceed 1.0")
        Double failureRate,

        @Min(value = 400, message = "Failure status code must be an HTTP error code >= 400")
        @Max(value = 599, message = "Failure status code must be an HTTP error code <= 599")
        Integer failureStatusCode,

        @Min(value = 0, message = "Forced failure count cannot be negative")
        Integer forcedFailureCount
) {
}
