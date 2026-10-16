package com.edvexa.gateway.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, Long> {

    Optional<IdempotencyRecordEntity> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey);

    @Modifying
    @Query("DELETE FROM IdempotencyRecordEntity r WHERE r.expiresAt < :now AND r.status != 'IN_PROGRESS'")
    int deleteExpiredRecords(@Param("now") Instant now);
}
