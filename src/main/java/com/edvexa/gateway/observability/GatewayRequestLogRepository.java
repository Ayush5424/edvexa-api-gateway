package com.edvexa.gateway.observability;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface GatewayRequestLogRepository extends JpaRepository<GatewayRequestLogEntity, Long> {

    List<GatewayRequestLogEntity> findByCorrelationId(String correlationId);

    List<GatewayRequestLogEntity> findByServiceId(String serviceId);

    List<GatewayRequestLogEntity> findTop50ByOrderByTimestampDesc();

    Page<GatewayRequestLogEntity> findAllByOrderByTimestampDesc(Pageable pageable);

    long countByOutcomeCategory(String outcomeCategory);

    @Query("SELECT r FROM GatewayRequestLogEntity r WHERE r.statusCode >= 400 OR r.outcomeCategory != 'SUCCESS' ORDER BY r.timestamp DESC")
    Page<GatewayRequestLogEntity> findRecentFailures(Pageable pageable);

    @Query("SELECT r FROM GatewayRequestLogEntity r WHERE " +
            "(:serviceId IS NULL OR r.serviceId = :serviceId) AND " +
            "(:clientId IS NULL OR r.clientId = :clientId) AND " +
            "(:outcome IS NULL OR r.outcomeCategory = :outcome) " +
            "ORDER BY r.timestamp DESC")
    Page<GatewayRequestLogEntity> findFilteredLogs(
            @Param("serviceId") String serviceId,
            @Param("clientId") String clientId,
            @Param("outcome") String outcome,
            Pageable pageable
    );

    @Query("SELECT r FROM GatewayRequestLogEntity r WHERE r.timestamp >= :since ORDER BY r.timestamp DESC")
    List<GatewayRequestLogEntity> findRecentLogs(@Param("since") Instant since);

    @Query("SELECT r.serviceId, COUNT(r) FROM GatewayRequestLogEntity r WHERE r.serviceId IS NOT NULL GROUP BY r.serviceId")
    List<Object[]> countRequestsByService();

    @Query("SELECT r.clientId, COUNT(r) FROM GatewayRequestLogEntity r WHERE r.clientId IS NOT NULL GROUP BY r.clientId")
    List<Object[]> countRequestsByClient();

    @Query("SELECT r.outcomeCategory, COUNT(r) FROM GatewayRequestLogEntity r GROUP BY r.outcomeCategory")
    List<Object[]> countRequestsByOutcome();

    @Query("SELECT COALESCE(SUM(r.retryCount), 0) FROM GatewayRequestLogEntity r")
    long sumTotalRetries();

    @Query("SELECT r.latencyMs FROM GatewayRequestLogEntity r")
    List<Long> findAllLatencies();
}
