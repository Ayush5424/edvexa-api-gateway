package com.edvexa.gateway.observability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "gateway_request_logs")
public class GatewayRequestLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correlation_id", length = 64, nullable = false)
    private String correlationId;

    @Column(name = "client_id", length = 64)
    private String clientId;

    @Column(name = "service_id", length = 64)
    private String serviceId;

    @Column(name = "http_method", length = 16, nullable = false)
    private String httpMethod;

    @Column(name = "request_path", length = 512, nullable = false)
    private String requestPath;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "circuit_breaker_state", length = 32)
    private String circuitBreakerState;

    @Column(name = "outcome_category", length = 32, nullable = false)
    private String outcomeCategory;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    public GatewayRequestLogEntity() {
    }

    public GatewayRequestLogEntity(
            String correlationId,
            String clientId,
            String serviceId,
            String httpMethod,
            String requestPath,
            int statusCode,
            long latencyMs,
            int retryCount,
            String circuitBreakerState,
            String outcomeCategory,
            String errorMessage
    ) {
        this.correlationId = correlationId;
        this.clientId = clientId;
        this.serviceId = serviceId;
        this.httpMethod = httpMethod;
        this.requestPath = requestPath;
        this.statusCode = statusCode;
        this.latencyMs = latencyMs;
        this.retryCount = retryCount;
        this.circuitBreakerState = circuitBreakerState;
        this.outcomeCategory = outcomeCategory;
        this.errorMessage = errorMessage;
    }

    @PrePersist
    protected void onCreate() {
        if (this.timestamp == null) {
            this.timestamp = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getCircuitBreakerState() {
        return circuitBreakerState;
    }

    public void setCircuitBreakerState(String circuitBreakerState) {
        this.circuitBreakerState = circuitBreakerState;
    }

    public String getOutcomeCategory() {
        return outcomeCategory;
    }

    public void setOutcomeCategory(String outcomeCategory) {
        this.outcomeCategory = outcomeCategory;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
