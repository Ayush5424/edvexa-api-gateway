package com.edvexa.gateway.idempotency;

import com.edvexa.gateway.common.error.IdempotencyConflictException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private Duration defaultTtl;

    @org.springframework.beans.factory.annotation.Autowired
    public IdempotencyService(
            IdempotencyRecordRepository repository,
            ObjectMapper objectMapper,
            @Value("${edvexa.gateway.idempotency.ttl-hours:24}") long ttlHours
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.defaultTtl = Duration.ofHours(ttlHours);
    }

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, 24);
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public String computeFingerprint(String method, String requestUri, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payloadRepresentation = (body != null && body.length > 0)
                    ? HexFormat.of().formatHex(body)
                    : "EMPTY";
            String rawString = method.toUpperCase() + ":" + requestUri + ":" + payloadRepresentation;
            byte[] hash = digest.digest(rawString.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyCheckResult processIdempotencyKey(
            String clientId,
            String idempotencyKey,
            String method,
            String requestUri,
            byte[] body
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return IdempotencyCheckResult.proceed();
        }

        String fingerprint = computeFingerprint(method, requestUri, body);
        Instant now = Instant.now();

        Optional<IdempotencyRecordEntity> existingOpt = repository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey);

        if (existingOpt.isPresent()) {
            IdempotencyRecordEntity record = existingOpt.get();

            // 1. Check if an active operation is currently in progress
            if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                log.warn("Concurrent duplicate request detected for client '{}' and key '{}'", clientId, idempotencyKey);
                throw new IdempotencyConflictException("An operation with idempotency key '" + idempotencyKey
                        + "' is currently in progress");
            }

            // 2. Check if record has expired or previously failed
            if (record.isExpired(now) || record.getStatus() == IdempotencyStatus.FAILED) {
                log.info("Idempotency record for client '{}' and key '{}' is expired or failed. Re-initiating as in-progress.",
                        clientId, idempotencyKey);
                record.setRequestHash(fingerprint);
                record.setStatus(IdempotencyStatus.IN_PROGRESS);
                record.setExpiresAt(now.plus(defaultTtl));
                record.setResponseCode(null);
                record.setResponseHeaders(null);
                record.setResponseBody(null);
                repository.saveAndFlush(record);
                return IdempotencyCheckResult.proceed();
            }

            // 3. For completed records, verify fingerprint matches
            if (!record.getRequestHash().equals(fingerprint)) {
                log.warn("Payload mismatch for idempotency key '{}' and client '{}'. Stored hash={}, incoming hash={}",
                        idempotencyKey, clientId, record.getRequestHash(), fingerprint);
                throw new IdempotencyConflictException("Idempotency key '" + idempotencyKey
                        + "' was previously used with a different request payload or endpoint");
            }

            // 4. Completed record with matching fingerprint -> Replay cached response
            if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                log.info("Replaying cached idempotent response for client '{}' and key '{}'", clientId, idempotencyKey);
                ResponseEntity<byte[]> cachedResponse = rebuildCachedResponse(record);
                return IdempotencyCheckResult.replay(cachedResponse);
            }
        }

        // Reserve in-progress slot with database uniqueness constraint
        try {
            IdempotencyRecordEntity newRecord = new IdempotencyRecordEntity(
                    clientId,
                    idempotencyKey,
                    fingerprint,
                    IdempotencyStatus.IN_PROGRESS,
                    now.plus(defaultTtl)
            );
            repository.saveAndFlush(newRecord);
            return IdempotencyCheckResult.proceed();
        } catch (DataIntegrityViolationException ex) {
            log.warn("Database unique constraint prevented concurrent race condition for key '{}': {}", idempotencyKey, ex.getMessage());
            throw new IdempotencyConflictException("An operation with idempotency key '" + idempotencyKey
                    + "' is currently in progress");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeOperation(
            String clientId,
            String idempotencyKey,
            int statusCode,
            HttpHeaders headers,
            byte[] bodyBytes
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        Optional<IdempotencyRecordEntity> opt = repository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey);
        if (opt.isPresent()) {
            IdempotencyRecordEntity record = opt.get();
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setResponseCode(statusCode);

            try {
                Map<String, List<String>> headerMap = headers != null ? headers : Map.of();
                record.setResponseHeaders(objectMapper.writeValueAsString(headerMap));
            } catch (Exception e) {
                log.warn("Failed to serialize headers for idempotency key '{}': {}", idempotencyKey, e.getMessage());
            }

            if (bodyBytes != null && bodyBytes.length > 0) {
                record.setResponseBody(Base64.getEncoder().encodeToString(bodyBytes));
            } else {
                record.setResponseBody(null);
            }

            repository.saveAndFlush(record);
            log.debug("Successfully saved completed idempotency record for client '{}' and key '{}'", clientId, idempotencyKey);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void abortOperation(String clientId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        Optional<IdempotencyRecordEntity> opt = repository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey);
        if (opt.isPresent()) {
            IdempotencyRecordEntity record = opt.get();
            // Delete the uncompleted in-progress record to prevent locking out future retries
            repository.delete(record);
            repository.flush();
            log.debug("Aborted and removed in-progress idempotency record for client '{}' and key '{}'", clientId, idempotencyKey);
        }
    }

    @Scheduled(fixedRate = 3600000) // Hourly cleanup
    @Transactional
    public int cleanupExpiredRecords() {
        int deleted = repository.deleteExpiredRecords(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired idempotency records", deleted);
        }
        return deleted;
    }

    private ResponseEntity<byte[]> rebuildCachedResponse(IdempotencyRecordEntity record) {
        HttpHeaders headers = new HttpHeaders();
        if (record.getResponseHeaders() != null && !record.getResponseHeaders().isBlank()) {
            try {
                Map<String, List<String>> map = objectMapper.readValue(
                        record.getResponseHeaders(),
                        new TypeReference<>() {}
                );
                map.forEach(headers::addAll);
            } catch (Exception e) {
                log.warn("Failed to deserialize response headers for key '{}': {}", record.getIdempotencyKey(), e.getMessage());
            }
        }

        // Add header to signify replayed idempotent response
        headers.set("X-Cache-Lookup", "HIT-IDEMPOTENT");

        byte[] bodyBytes = new byte[0];
        if (record.getResponseBody() != null && !record.getResponseBody().isBlank()) {
            bodyBytes = Base64.getDecoder().decode(record.getResponseBody());
        }

        int status = record.getResponseCode() != null ? record.getResponseCode() : 200;
        return ResponseEntity.status(status)
                .headers(headers)
                .body(bodyBytes);
    }
}
