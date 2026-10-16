package com.edvexa.gateway.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RedisRateLimiterService implements RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiterService.class);
    private static final String KEY_PREFIX = "rate_limit:";
    private static final long WINDOW_MS = 60_000L;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> redisScript;
    private final ConcurrentHashMap<String, Deque<Long>> inMemoryFallback = new ConcurrentHashMap<>();

    public RedisRateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisScript = new DefaultRedisScript<>();
        this.redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/rate_limiter.lua")));
        this.redisScript.setResultType(List.class);
    }

    @Override
    public RateLimitResult tryAcquire(String clientId, int limitPerMinute) {
        if (clientId == null || clientId.isBlank()) {
            clientId = "anonymous";
        }
        if (limitPerMinute <= 0) {
            limitPerMinute = 60;
        }

        String key = KEY_PREFIX + clientId;
        long now = System.currentTimeMillis();
        String member = now + ":" + UUID.randomUUID().toString().substring(0, 8);

        try {
            if (redisTemplate != null) {
                @SuppressWarnings("unchecked")
                List<Long> result = redisTemplate.execute(
                        redisScript,
                        List.of(key),
                        String.valueOf(now),
                        String.valueOf(WINDOW_MS),
                        String.valueOf(limitPerMinute),
                        member
                );

                if (result != null && result.size() >= 5) {
                    boolean allowed = result.get(0) == 1L;
                    long limit = result.get(1);
                    long remaining = result.get(2);
                    long resetSeconds = result.get(3);
                    long retryAfter = result.get(4);

                    return new RateLimitResult(allowed, limit, remaining, resetSeconds, retryAfter);
                }
            }
        } catch (Exception e) {
            log.warn("Redis rate limit execution failed for client={}; falling back to local memory: {}", clientId, e.getMessage());
        }

        return fallbackAcquire(clientId, limitPerMinute, now);
    }

    private synchronized RateLimitResult fallbackAcquire(String clientId, int limitPerMinute, long now) {
        long clearBefore = now - WINDOW_MS;
        Deque<Long> timestamps = inMemoryFallback.computeIfAbsent(clientId, k -> new ArrayDeque<>());

        while (!timestamps.isEmpty() && timestamps.peekFirst() <= clearBefore) {
            timestamps.pollFirst();
        }

        if (timestamps.size() < limitPerMinute) {
            timestamps.addLast(now);
            long remaining = limitPerMinute - timestamps.size();
            long resetSeconds = Math.max(1, WINDOW_MS / 1000);
            return RateLimitResult.allowed(limitPerMinute, remaining, resetSeconds);
        } else {
            long oldest = timestamps.peekFirst() != null ? timestamps.peekFirst() : now;
            long timeUntilExpire = (oldest + WINDOW_MS) - now;
            long retryAfter = Math.max(1, (long) Math.ceil(timeUntilExpire / 1000.0));
            return RateLimitResult.denied(limitPerMinute, retryAfter);
        }
    }
}
