package com.edvexa.gateway.idempotency;

import org.springframework.http.ResponseEntity;

public record IdempotencyCheckResult(
        Action action,
        ResponseEntity<byte[]> cachedResponse
) {
    public enum Action {
        PROCEED,
        REPLAY
    }

    public static IdempotencyCheckResult proceed() {
        return new IdempotencyCheckResult(Action.PROCEED, null);
    }

    public static IdempotencyCheckResult replay(ResponseEntity<byte[]> response) {
        return new IdempotencyCheckResult(Action.REPLAY, response);
    }
}
