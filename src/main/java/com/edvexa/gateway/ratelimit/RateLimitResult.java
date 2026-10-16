package com.edvexa.gateway.ratelimit;

public record RateLimitResult(
        boolean allowed,
        long limit,
        long remaining,
        long resetSeconds,
        long retryAfterSeconds
) {
    public static RateLimitResult allowed(long limit, long remaining, long resetSeconds) {
        return new RateLimitResult(true, limit, remaining, resetSeconds, 0);
    }

    public static RateLimitResult denied(long limit, long retryAfterSeconds) {
        return new RateLimitResult(false, limit, 0, retryAfterSeconds, retryAfterSeconds);
    }
}
