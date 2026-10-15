package com.edvexa.gateway.common.error;

public class CircuitBreakerOpenException extends RuntimeException {
    public CircuitBreakerOpenException(String serviceName) {
        super("Circuit breaker is OPEN for service: " + serviceName);
    }
}
