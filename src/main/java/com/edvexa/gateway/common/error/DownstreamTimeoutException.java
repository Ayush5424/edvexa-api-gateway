package com.edvexa.gateway.common.error;

public class DownstreamTimeoutException extends RuntimeException {
    public DownstreamTimeoutException(String message) {
        super(message);
    }
}
