package com.edvexa.gateway.observability;

import com.edvexa.gateway.observability.dto.GatewayMetricsDto;
import com.edvexa.gateway.observability.dto.RecentErrorDto;
import com.edvexa.gateway.observability.dto.ServiceHealthDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Observability", description = "Gateway metrics, request audit logs, and service health monitoring")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminObservabilityController {

    private final GatewayMetricsService metricsService;
    private final ServiceHealthService healthService;
    private final GatewayRequestLogRepository logRepository;

    public AdminObservabilityController(
            GatewayMetricsService metricsService,
            ServiceHealthService healthService,
            GatewayRequestLogRepository logRepository
    ) {
        this.metricsService = metricsService;
        this.healthService = healthService;
        this.logRepository = logRepository;
    }

    @GetMapping("/metrics")
    @Operation(summary = "Get aggregated gateway performance, latency percentiles, and request metrics")
    public ResponseEntity<GatewayMetricsDto> getMetrics() {
        return ResponseEntity.ok(metricsService.getMetricsSummary());
    }

    @GetMapping({"/services/health", "/health"})
    @Operation(summary = "Get registered downstream service health and circuit-breaker states")
    public ResponseEntity<List<ServiceHealthDto>> getServiceHealth() {
        return ResponseEntity.ok(healthService.getAllServiceHealth());
    }

    @GetMapping("/services/{serviceId}/health")
    @Operation(summary = "Get health and circuit-breaker details for a specific service")
    public ResponseEntity<ServiceHealthDto> getSingleServiceHealth(@PathVariable String serviceId) {
        return ResponseEntity.ok(healthService.getServiceHealth(serviceId));
    }

    @GetMapping("/audit/logs")
    @Operation(summary = "Get paginated audit logs with optional filters")
    public ResponseEntity<Page<GatewayRequestLogEntity>> getAuditLogs(
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String outcome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        return ResponseEntity.ok(logRepository.findFilteredLogs(serviceId, clientId, outcome, pageable));
    }

    @GetMapping("/audit/errors")
    @Operation(summary = "Get paginated recent gateway failures and error logs")
    public ResponseEntity<Page<RecentErrorDto>> getRecentErrors(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        Page<RecentErrorDto> errors = logRepository.findRecentFailures(pageable)
                .map(RecentErrorDto::fromEntity);
        return ResponseEntity.ok(errors);
    }

    @GetMapping("/audit/correlation/{correlationId}")
    @Operation(summary = "Find all audit entries for a specific correlation ID")
    public ResponseEntity<List<GatewayRequestLogEntity>> getLogsByCorrelationId(
            @PathVariable String correlationId
    ) {
        return ResponseEntity.ok(logRepository.findByCorrelationId(correlationId));
    }
}
