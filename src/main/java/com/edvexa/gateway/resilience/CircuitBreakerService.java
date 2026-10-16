package com.edvexa.gateway.resilience;

import com.edvexa.gateway.common.error.CircuitBreakerOpenException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CircuitBreakerService {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerService.class);

    private final CircuitBreakerRegistry registry;
    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    private int slidingWindowSize = 5;
    private int minimumNumberOfCalls = 3;
    private float failureRateThreshold = 50.0f;
    private Duration waitDurationInOpenState = Duration.ofMillis(3000);
    private int permittedNumberOfCallsInHalfOpen = 2;

    public CircuitBreakerService() {
        CircuitBreakerConfig defaultConfig = createConfig();
        this.registry = CircuitBreakerRegistry.of(defaultConfig);
    }

    public CircuitBreakerService(
            int slidingWindowSize,
            int minimumNumberOfCalls,
            float failureRateThreshold,
            Duration waitDurationInOpenState,
            int permittedNumberOfCallsInHalfOpen
    ) {
        this.slidingWindowSize = slidingWindowSize;
        this.minimumNumberOfCalls = minimumNumberOfCalls;
        this.failureRateThreshold = failureRateThreshold;
        this.waitDurationInOpenState = waitDurationInOpenState;
        this.permittedNumberOfCallsInHalfOpen = permittedNumberOfCallsInHalfOpen;

        CircuitBreakerConfig config = createConfig();
        this.registry = CircuitBreakerRegistry.of(config);
    }

    private CircuitBreakerConfig createConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(waitDurationInOpenState)
                .permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpen)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
    }

    public CircuitBreaker getCircuitBreaker(String serviceId) {
        return breakers.computeIfAbsent(serviceId, id -> {
            CircuitBreaker cb = registry.circuitBreaker(id);
            cb.getEventPublisher()
                    .onStateTransition(event -> log.info("Circuit breaker for service [{}] transitioned: {}", id, event.getStateTransition()));
            return cb;
        });
    }

    public record CircuitBreakerDetails(
            String state,
            float failureRate,
            float slowCallRate,
            int numberOfBufferedCalls,
            int numberOfFailedCalls,
            int numberOfSuccessfulCalls,
            long numberOfNotPermittedCalls
    ) {}

    public String getCircuitBreakerState(String serviceId) {
        CircuitBreaker cb = breakers.get(serviceId);
        return cb != null ? cb.getState().name() : CircuitBreaker.State.CLOSED.name();
    }

    public CircuitBreakerDetails getCircuitBreakerDetails(String serviceId) {
        CircuitBreaker cb = breakers.get(serviceId);
        if (cb == null) {
            return new CircuitBreakerDetails(CircuitBreaker.State.CLOSED.name(), 0.0f, 0.0f, 0, 0, 0, 0L);
        }
        CircuitBreaker.Metrics m = cb.getMetrics();
        float failureRate = m.getFailureRate() < 0 ? 0.0f : m.getFailureRate();
        float slowCallRate = m.getSlowCallRate() < 0 ? 0.0f : m.getSlowCallRate();
        return new CircuitBreakerDetails(
                cb.getState().name(),
                failureRate,
                slowCallRate,
                m.getNumberOfBufferedCalls(),
                m.getNumberOfFailedCalls(),
                m.getNumberOfSuccessfulCalls(),
                m.getNumberOfNotPermittedCalls()
        );
    }

    public void checkCallPermitted(String serviceId, String serviceName) {
        CircuitBreaker cb = getCircuitBreaker(serviceId);
        if (cb.getState() == CircuitBreaker.State.OPEN) {
            throw new CircuitBreakerOpenException(serviceName != null ? serviceName : serviceId);
        }
    }

    public void recordSuccess(String serviceId, long durationMs) {
        CircuitBreaker cb = getCircuitBreaker(serviceId);
        cb.onSuccess(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordFailure(String serviceId, long durationMs, Throwable throwable) {
        CircuitBreaker cb = getCircuitBreaker(serviceId);
        cb.onError(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS, throwable);
    }

    public void reset(String serviceId) {
        CircuitBreaker cb = breakers.get(serviceId);
        if (cb != null) {
            cb.reset();
        }
    }

    public void resetAll() {
        breakers.values().forEach(CircuitBreaker::reset);
    }
}
