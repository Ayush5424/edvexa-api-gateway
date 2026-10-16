package com.edvexa.gateway.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisRateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RedisRateLimiterService(redisTemplate);
    }

    @Test
    @DisplayName("Should allow request and decrement remaining when Redis script returns success")
    void shouldAllowRequestWhenWithinLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(1L, 60L, 59L, 60L, 0L));

        RateLimitResult result = rateLimiterService.tryAcquire("client-test-1", 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(60L);
        assertThat(result.remaining()).isEqualTo(59L);
        assertThat(result.resetSeconds()).isEqualTo(60L);
        assertThat(result.retryAfterSeconds()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should deny request and provide retry-after when Redis script returns limit exceeded")
    void shouldDenyRequestWhenLimitExceeded() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(0L, 10L, 0L, 15L, 15L));

        RateLimitResult result = rateLimiterService.tryAcquire("client-test-2", 10);

        assertThat(result.allowed()).isFalse();
        assertThat(result.limit()).isEqualTo(10L);
        assertThat(result.remaining()).isEqualTo(0L);
        assertThat(result.retryAfterSeconds()).isEqualTo(15L);
    }

    @Test
    @DisplayName("Should gracefully fallback to in-memory sliding window when Redis throws an exception")
    void shouldFallbackToInMemoryOnRedisFailure() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        // Limit is 3 requests per minute
        RateLimitResult req1 = rateLimiterService.tryAcquire("client-fallback", 3);
        RateLimitResult req2 = rateLimiterService.tryAcquire("client-fallback", 3);
        RateLimitResult req3 = rateLimiterService.tryAcquire("client-fallback", 3);
        RateLimitResult req4 = rateLimiterService.tryAcquire("client-fallback", 3);

        assertThat(req1.allowed()).isTrue();
        assertThat(req1.remaining()).isEqualTo(2);

        assertThat(req2.allowed()).isTrue();
        assertThat(req2.remaining()).isEqualTo(1);

        assertThat(req3.allowed()).isTrue();
        assertThat(req3.remaining()).isEqualTo(0);

        assertThat(req4.allowed()).isFalse();
        assertThat(req4.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should maintain independent rate limit counters for different clients in fallback mode")
    void shouldIsolateLimitsBetweenClients() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis unavailable"));

        // Client A has limit 1, Client B has limit 5
        RateLimitResult a1 = rateLimiterService.tryAcquire("client-A", 1);
        RateLimitResult a2 = rateLimiterService.tryAcquire("client-A", 1);

        RateLimitResult b1 = rateLimiterService.tryAcquire("client-B", 5);
        RateLimitResult b2 = rateLimiterService.tryAcquire("client-B", 5);

        assertThat(a1.allowed()).isTrue();
        assertThat(a2.allowed()).isFalse();

        assertThat(b1.allowed()).isTrue();
        assertThat(b2.allowed()).isTrue();
    }

    @Test
    @DisplayName("Should handle concurrent requests safely without exceeding configured limit")
    void shouldHandleConcurrentRequestsSafely() throws InterruptedException {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis unavailable"));

        int totalThreads = 20;
        int limit = 10;
        String clientId = "client-concurrent";

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger deniedCount = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    RateLimitResult result = rateLimiterService.tryAcquire(clientId, limit);
                    if (result.allowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        deniedCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(allowedCount.get()).isEqualTo(limit);
        assertThat(deniedCount.get()).isEqualTo(totalThreads - limit);
    }
}
