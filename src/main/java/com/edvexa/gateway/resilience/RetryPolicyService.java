package com.edvexa.gateway.resilience;

import com.edvexa.gateway.common.error.CircuitBreakerOpenException;
import com.edvexa.gateway.common.error.DownstreamTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

@Service
public class RetryPolicyService {

    private static final Logger log = LoggerFactory.getLogger(RetryPolicyService.class);

    private int maxAttempts = 3;
    private long initialBackoffMs = 100;
    private double backoffMultiplier = 2.0;
    private long maxBackoffMs = 2000;

    public RetryPolicyService() {
    }

    public RetryPolicyService(int maxAttempts, long initialBackoffMs, double backoffMultiplier, long maxBackoffMs) {
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxBackoffMs = maxBackoffMs;
    }

    public boolean isTransientException(Throwable throwable) {
        if (throwable instanceof CircuitBreakerOpenException) {
            return false;
        }
        if (throwable instanceof DownstreamTimeoutException || throwable instanceof HttpTimeoutException || throwable instanceof SocketTimeoutException) {
            return true;
        }
        if (throwable instanceof ConnectException) {
            return true;
        }
        if (throwable instanceof ResourceAccessException) {
            Throwable cause = throwable.getCause();
            if (cause != null && (cause instanceof IOException || cause instanceof ConnectException || cause instanceof SocketTimeoutException)) {
                return true;
            }
            return true;
        }
        if (throwable instanceof HttpStatusCodeException statusEx) {
            return isTransientStatus(statusEx.getStatusCode().value());
        }
        return false;
    }

    public boolean isTransientStatus(int statusCode) {
        return statusCode == HttpStatus.BAD_GATEWAY.value()
                || statusCode == HttpStatus.SERVICE_UNAVAILABLE.value()
                || statusCode == HttpStatus.GATEWAY_TIMEOUT.value();
    }

    public <T> RetryExecutionResult<T> executeWithRetry(
            String serviceId,
            RetryableOperation<T> operation
    ) throws Exception {
        int attempt = 0;
        long currentBackoff = initialBackoffMs;
        Exception lastException = null;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                T result = operation.execute();

                // Check if response is a ResponseEntity with a retryable transient status code
                if (result instanceof ResponseEntity<?> responseEntity) {
                    int statusCode = responseEntity.getStatusCode().value();
                    if (isTransientStatus(statusCode) && attempt < maxAttempts) {
                        log.warn("Downstream service [{}] returned transient status code {}. Attempt {}/{} failed. Backing off {}ms",
                                serviceId, statusCode, attempt, maxAttempts, currentBackoff);
                        sleep(currentBackoff);
                        currentBackoff = Math.min(maxBackoffMs, (long) (currentBackoff * backoffMultiplier));
                        continue;
                    }
                }

                int retriesPerformed = attempt - 1;
                return new RetryExecutionResult<>(result, retriesPerformed);

            } catch (Exception ex) {
                lastException = ex;

                if (!isTransientException(ex)) {
                    log.debug("Non-transient exception encountered for service [{}], failing fast without retries: {}", serviceId, ex.getMessage());
                    throw ex;
                }

                if (attempt >= maxAttempts) {
                    log.warn("Exhausted all {} retry attempts for service [{}]: {}", maxAttempts, serviceId, ex.getMessage());
                    throw ex;
                }

                log.warn("Transient failure calling downstream service [{}] on attempt {}/{}: {}. Retrying in {}ms",
                        serviceId, attempt, maxAttempts, ex.getMessage(), currentBackoff);
                sleep(currentBackoff);
                currentBackoff = Math.min(maxBackoffMs, (long) (currentBackoff * backoffMultiplier));
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new IllegalStateException("Retry loop terminated unexpectedly without result or exception");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry backoff interrupted", e);
        }
    }

    @FunctionalInterface
    public interface RetryableOperation<T> {
        T execute() throws Exception;
    }

    public record RetryExecutionResult<T>(T result, int retries) {}
}
