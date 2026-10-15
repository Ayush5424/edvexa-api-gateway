package com.edvexa.gateway.observability;

import com.edvexa.gateway.common.error.ResourceNotFoundException;
import com.edvexa.gateway.observability.dto.ServiceHealthDto;
import com.edvexa.gateway.registry.ServiceEntity;
import com.edvexa.gateway.registry.ServiceRepository;
import com.edvexa.gateway.resilience.CircuitBreakerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ServiceHealthService {

    private final ServiceRepository serviceRepository;
    private final CircuitBreakerService circuitBreakerService;

    public ServiceHealthService(
            ServiceRepository serviceRepository,
            CircuitBreakerService circuitBreakerService
    ) {
        this.serviceRepository = serviceRepository;
        this.circuitBreakerService = circuitBreakerService;
    }

    public List<ServiceHealthDto> getAllServiceHealth() {
        return serviceRepository.findAll().stream()
                .map(this::buildServiceHealth)
                .toList();
    }

    public ServiceHealthDto getServiceHealth(String serviceId) {
        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + serviceId));
        return buildServiceHealth(service);
    }

    private ServiceHealthDto buildServiceHealth(ServiceEntity service) {
        CircuitBreakerService.CircuitBreakerDetails cb = circuitBreakerService.getCircuitBreakerDetails(service.getId());

        String overallStatus;
        if (!service.isEnabled()) {
            overallStatus = "DISABLED";
        } else if ("OPEN".equalsIgnoreCase(cb.state())) {
            overallStatus = "UNHEALTHY";
        } else if ("HALF_OPEN".equalsIgnoreCase(cb.state())) {
            overallStatus = "DEGRADED";
        } else {
            overallStatus = "HEALTHY";
        }

        return new ServiceHealthDto(
                service.getId(),
                service.getName(),
                service.getPathPrefix(),
                service.getBaseUrl(),
                service.isEnabled(),
                cb.state(),
                cb.failureRate(),
                cb.slowCallRate(),
                cb.numberOfBufferedCalls(),
                cb.numberOfFailedCalls(),
                cb.numberOfSuccessfulCalls(),
                cb.numberOfNotPermittedCalls(),
                service.getConnectionTimeoutMs(),
                service.getResponseTimeoutMs(),
                overallStatus
        );
    }
}
