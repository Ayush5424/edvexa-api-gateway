package com.edvexa.gateway.ratelimit;

public interface RateLimiterService {

    /**
     * Attempts to acquire a rate limit permit for the given client within a 60-second sliding window.
     *
     * @param clientId identifier of the client making the request
     * @param limitPerMinute maximum allowed requests per minute for this client
     * @return RateLimitResult indicating whether the request is allowed, remaining quota, and reset times
     */
    RateLimitResult tryAcquire(String clientId, int limitPerMinute);
}
