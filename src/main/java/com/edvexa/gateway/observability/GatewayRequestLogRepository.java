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

    @Query("SELECT r FROM GatewayRequestLogEntity r WHERE r.timestamp >= :since ORDER BY r.timestamp DESC")
    List<GatewayRequestLogEntity> findRecentLogs(@Param("since") Instant since);

    @Query("SELECT r.serviceId, COUNT(r) FROM GatewayRequestLogEntity r GROUP BY r.serviceId")
    List<Object[]> countRequestsByService();
}
