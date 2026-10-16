package com.edvexa.gateway.idempotency;

import com.edvexa.gateway.common.error.IdempotencyConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository repository;

    private ObjectMapper objectMapper;
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        idempotencyService = new IdempotencyService(repository, objectMapper, 24);
    }

    @Test
    @DisplayName("computeFingerprint should produce deterministic SHA-256 hash")
    void testComputeFingerprint_deterministic() {
        byte[] body = "{\"item\":\"laptop\",\"amount\":1200}".getBytes(StandardCharsets.UTF_8);
        String hash1 = idempotencyService.computeFingerprint("POST", "/orders", body);
        String hash2 = idempotencyService.computeFingerprint("POST", "/orders", body);

        assertThat(hash1).isNotNull().isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    @Test
    @DisplayName("computeFingerprint should differ for different bodies or endpoints")
    void testComputeFingerprint_differentInputs() {
        byte[] body1 = "{\"item\":\"laptop\"}".getBytes(StandardCharsets.UTF_8);
        byte[] body2 = "{\"item\":\"phone\"}".getBytes(StandardCharsets.UTF_8);

        String hash1 = idempotencyService.computeFingerprint("POST", "/orders", body1);
        String hash2 = idempotencyService.computeFingerprint("POST", "/orders", body2);
        String hash3 = idempotencyService.computeFingerprint("PUT", "/orders", body1);
        String hash4 = idempotencyService.computeFingerprint("POST", "/payments", body1);

        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(hash1).isNotEqualTo(hash3);
        assertThat(hash1).isNotEqualTo(hash4);
    }

    @Test
    @DisplayName("processIdempotencyKey with null or blank key returns PROCEED without repository interaction")
    void testProcessIdempotencyKey_blankKey() {
        IdempotencyCheckResult result1 = idempotencyService.processIdempotencyKey("client1", null, "POST", "/orders", null);
        IdempotencyCheckResult result2 = idempotencyService.processIdempotencyKey("client1", "   ", "POST", "/orders", null);

        assertThat(result1.action()).isEqualTo(IdempotencyCheckResult.Action.PROCEED);
        assertThat(result2.action()).isEqualTo(IdempotencyCheckResult.Action.PROCEED);
        verify(repository, never()).findByClientIdAndIdempotencyKey(any(), any());
    }

    @Test
    @DisplayName("processIdempotencyKey for new key reserves IN_PROGRESS record and returns PROCEED")
    void testProcessIdempotencyKey_newKey() {
        String clientId = "client-alpha";
        String key = "key-12345";
        byte[] body = "{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8);

        when(repository.findByClientIdAndIdempotencyKey(clientId, key)).thenReturn(Optional.empty());

        IdempotencyCheckResult result = idempotencyService.processIdempotencyKey(clientId, key, "POST", "/orders", body);

        assertThat(result.action()).isEqualTo(IdempotencyCheckResult.Action.PROCEED);

        ArgumentCaptor<IdempotencyRecordEntity> captor = ArgumentCaptor.forClass(IdempotencyRecordEntity.class);
        verify(repository).saveAndFlush(captor.capture());

        IdempotencyRecordEntity saved = captor.getValue();
        assertThat(saved.getClientId()).isEqualTo(clientId);
        assertThat(saved.getIdempotencyKey()).isEqualTo(key);
        assertThat(saved.getStatus()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("processIdempotencyKey throws conflict if operation is currently IN_PROGRESS")
    void testProcessIdempotencyKey_inProgressConflict() {
        String clientId = "client-alpha";
        String key = "key-12345";
        byte[] body = "{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8);
        String hash = idempotencyService.computeFingerprint("POST", "/orders", body);

        IdempotencyRecordEntity existing = new IdempotencyRecordEntity(
                clientId, key, hash, IdempotencyStatus.IN_PROGRESS, Instant.now().plus(Duration.ofHours(1))
        );
        when(repository.findByClientIdAndIdempotencyKey(clientId, key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.processIdempotencyKey(clientId, key, "POST", "/orders", body))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("is currently in progress");
    }

    @Test
    @DisplayName("processIdempotencyKey returns REPLAY with cached response for COMPLETED matching record")
    void testProcessIdempotencyKey_completedReplay() throws Exception {
        String clientId = "client-alpha";
        String key = "key-12345";
        byte[] body = "{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8);
        String hash = idempotencyService.computeFingerprint("POST", "/orders", body);

        IdempotencyRecordEntity existing = new IdempotencyRecordEntity(
                clientId, key, hash, IdempotencyStatus.COMPLETED, Instant.now().plus(Duration.ofHours(1))
        );
        existing.setResponseCode(201);
        existing.setResponseBody(java.util.Base64.getEncoder().encodeToString("{\"orderId\":\"ord-99\"}".getBytes(StandardCharsets.UTF_8)));
        existing.setResponseHeaders(objectMapper.writeValueAsString(java.util.Map.of("Content-Type", java.util.List.of("application/json"))));

        when(repository.findByClientIdAndIdempotencyKey(clientId, key)).thenReturn(Optional.of(existing));

        IdempotencyCheckResult result = idempotencyService.processIdempotencyKey(clientId, key, "POST", "/orders", body);

        assertThat(result.action()).isEqualTo(IdempotencyCheckResult.Action.REPLAY);
        assertThat(result.cachedResponse()).isNotNull();
        assertThat(result.cachedResponse().getStatusCode().value()).isEqualTo(201);
        assertThat(new String(result.cachedResponse().getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"orderId\":\"ord-99\"}");
        assertThat(result.cachedResponse().getHeaders().getFirst("X-Cache-Lookup")).isEqualTo("HIT-IDEMPOTENT");
    }

    @Test
    @DisplayName("processIdempotencyKey throws conflict when COMPLETED record has different payload")
    void testProcessIdempotencyKey_payloadMismatchConflict() {
        String clientId = "client-alpha";
        String key = "key-12345";
        byte[] originalBody = "{\"name\":\"original\"}".getBytes(StandardCharsets.UTF_8);
        byte[] newBody = "{\"name\":\"altered\"}".getBytes(StandardCharsets.UTF_8);

        String originalHash = idempotencyService.computeFingerprint("POST", "/orders", originalBody);

        IdempotencyRecordEntity existing = new IdempotencyRecordEntity(
                clientId, key, originalHash, IdempotencyStatus.COMPLETED, Instant.now().plus(Duration.ofHours(1))
        );
        when(repository.findByClientIdAndIdempotencyKey(clientId, key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.processIdempotencyKey(clientId, key, "POST", "/orders", newBody))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("previously used with a different request payload or endpoint");
    }

    @Test
    @DisplayName("processIdempotencyKey allows retry when existing record is expired and not in progress")
    void testProcessIdempotencyKey_expiredRecordAllowsRetry() {
        String clientId = "client-alpha";
        String key = "key-12345";
        byte[] body = "{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8);
        String hash = idempotencyService.computeFingerprint("POST", "/orders", body);

        IdempotencyRecordEntity expiredRecord = new IdempotencyRecordEntity(
                clientId, key, hash, IdempotencyStatus.COMPLETED, Instant.now().minus(Duration.ofMinutes(10))
        );
        when(repository.findByClientIdAndIdempotencyKey(clientId, key)).thenReturn(Optional.of(expiredRecord));

        IdempotencyCheckResult result = idempotencyService.processIdempotencyKey(clientId, key, "POST", "/orders", body);

        assertThat(result.action()).isEqualTo(IdempotencyCheckResult.Action.PROCEED);
        assertThat(expiredRecord.getStatus()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
        verify(repository).saveAndFlush(expiredRecord);
    }

    @Test
    @DisplayName("processIdempotencyKey catches DataIntegrityViolationException on concurrent insert race and throws conflict")
    void testProcessIdempotencyKey_concurrentInsertRaceThrowsConflict() {
        String clientId = "client-alpha";
        String key = "key-12345";
        byte[] body = "{\"name\":\"test\"}".getBytes(StandardCharsets.UTF_8);

        when(repository.findByClientIdAndIdempotencyKey(clientId, key)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Duplicate key uk_client_idempotency"));

        assertThatThrownBy(() -> idempotencyService.processIdempotencyKey(clientId, key, "POST", "/orders", body))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("is currently in progress");
    }

    @Test
    @DisplayName("completeOperation updates existing record with response status, headers, and body")
    void testCompleteOperation() {
        String clientId = "client-alpha";
        String key = "key-12345";
        byte[] bodyBytes = "{\"status\":\"created\"}".getBytes(StandardCharsets.UTF_8);

        IdempotencyRecordEntity record = new IdempotencyRecordEntity(
                clientId, key, "hash123", IdempotencyStatus.IN_PROGRESS, Instant.now().plus(Duration.ofHours(1))
        );
        when(repository.findByClientIdAndIdempotencyKey(clientId, key)).thenReturn(Optional.of(record));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        idempotencyService.completeOperation(clientId, key, 201, headers, bodyBytes);

        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(record.getResponseCode()).isEqualTo(201);
        assertThat(record.getResponseBody()).isEqualTo(java.util.Base64.getEncoder().encodeToString(bodyBytes));
        assertThat(record.getResponseHeaders()).contains("Content-Type");
        verify(repository).saveAndFlush(record);
    }

    @Test
    @DisplayName("abortOperation deletes record to unblock retries")
    void testAbortOperation() {
        String clientId = "client-alpha";
        String key = "key-12345";

        IdempotencyRecordEntity record = new IdempotencyRecordEntity(
                clientId, key, "hash123", IdempotencyStatus.IN_PROGRESS, Instant.now().plus(Duration.ofHours(1))
        );
        when(repository.findByClientIdAndIdempotencyKey(clientId, key)).thenReturn(Optional.of(record));

        idempotencyService.abortOperation(clientId, key);

        verify(repository).delete(record);
        verify(repository).flush();
    }

    @Test
    @DisplayName("cleanupExpiredRecords calls repository deleteExpiredRecords")
    void testCleanupExpiredRecords() {
        when(repository.deleteExpiredRecords(any(Instant.class))).thenReturn(5);

        int count = idempotencyService.cleanupExpiredRecords();

        assertThat(count).isEqualTo(5);
        verify(repository).deleteExpiredRecords(any(Instant.class));
    }
}
