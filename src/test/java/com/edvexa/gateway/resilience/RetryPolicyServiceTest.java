package com.edvexa.gateway.resilience;

import com.edvexa.gateway.common.error.CircuitBreakerOpenException;
import com.edvexa.gateway.common.error.DownstreamTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyServiceTest {

    private final RetryPolicyService retryPolicyService = new RetryPolicyService(3, 10, 2.0, 100);

    @Test
    @DisplayName("Should succeed on initial attempt without retries")
    void shouldSucceedOnInitialAttempt() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);

        RetryPolicyService.RetryExecutionResult<String> result = retryPolicyService.executeWithRetry("srv-test", () -> {
            attempts.incrementAndGet();
            return "SUCCESS";
        });

        assertThat(result.result()).isEqualTo("SUCCESS");
        assertThat(result.retries()).isEqualTo(0);
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should retry transient exception and return result when downstream recovers")
    void shouldRetryTransientExceptionAndSucceed() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);

        RetryPolicyService.RetryExecutionResult<String> result = retryPolicyService.executeWithRetry("srv-test", () -> {
            int current = attempts.incrementAndGet();
            if (current == 1) {
                throw new ResourceAccessException("Connection refused", new ConnectException("Connection refused"));
            }
            return "RECOVERED";
        });

        assertThat(result.result()).isEqualTo("RECOVERED");
        assertThat(result.retries()).isEqualTo(1);
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should retry when downstream returns 503 transient status code")
    void shouldRetryOnTransient503StatusCode() throws Exception {
        AtomicInteger attempts = new AtomicInteger(0);

        RetryPolicyService.RetryExecutionResult<ResponseEntity<String>> result = retryPolicyService.executeWithRetry("srv-test", () -> {
            int current = attempts.incrementAndGet();
            if (current == 1) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Downstream degraded");
            }
            return ResponseEntity.ok("OK");
        });

        assertThat(result.result().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.result().getBody()).isEqualTo("OK");
        assertThat(result.retries()).isEqualTo(1);
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should fail fast on 4xx client errors without executing retries")
    void shouldFailFastOnClientError() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retryPolicyService.executeWithRetry("srv-test", () -> {
            attempts.incrementAndGet();
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Invalid input");
        })).isInstanceOf(HttpClientErrorException.class);

        // Only 1 attempt made: zero retries
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should never retry when CircuitBreakerOpenException is encountered")
    void shouldNotRetryCircuitBreakerOpen() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retryPolicyService.executeWithRetry("srv-test", () -> {
            attempts.incrementAndGet();
            throw new CircuitBreakerOpenException("srv-test");
        })).isInstanceOf(CircuitBreakerOpenException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should exhaust bounded retries and throw exception when transient failure persists")
    void shouldExhaustRetriesOnPersistentFailure() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> retryPolicyService.executeWithRetry("srv-test", () -> {
            attempts.incrementAndGet();
            throw new DownstreamTimeoutException("Read timed out");
        })).isInstanceOf(DownstreamTimeoutException.class);

        // Max attempts is 3
        assertThat(attempts.get()).isEqualTo(3);
    }
}
