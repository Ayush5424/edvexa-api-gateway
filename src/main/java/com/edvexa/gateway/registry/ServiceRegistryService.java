package com.edvexa.gateway.registry;

import com.edvexa.gateway.common.error.ResourceNotFoundException;
import com.edvexa.gateway.common.error.ServiceUnavailableException;
import com.edvexa.gateway.registry.dto.ServiceRegistrationRequest;
import com.edvexa.gateway.registry.dto.ServiceResponse;
import com.edvexa.gateway.registry.dto.ServiceUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ServiceRegistryService {

    private final ServiceRepository serviceRepository;
    private final UrlValidatorUtil urlValidatorUtil;

    public ServiceRegistryService(ServiceRepository serviceRepository, UrlValidatorUtil urlValidatorUtil) {
        this.serviceRepository = serviceRepository;
        this.urlValidatorUtil = urlValidatorUtil;
    }

    public ServiceResponse registerService(ServiceRegistrationRequest request) {
        String normalizedPrefix = normalizePathPrefix(request.pathPrefix());

        if (serviceRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("Service with name '" + request.name() + "' already exists");
        }

        if (serviceRepository.existsByPathPrefix(normalizedPrefix)) {
            throw new IllegalArgumentException("Service with path prefix '" + normalizedPrefix + "' already exists");
        }

        urlValidatorUtil.validate(request.baseUrl());

        ServiceEntity entity = new ServiceEntity(
                null,
                request.name(),
                normalizeBaseUrl(request.baseUrl()),
                normalizedPrefix,
                request.connectionTimeoutMs(),
                request.responseTimeoutMs(),
                request.enabled()
        );

        ServiceEntity saved = serviceRepository.save(entity);
        return ServiceResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> getAllServices() {
        return serviceRepository.findAll().stream()
                .map(ServiceResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceResponse getServiceById(String id) {
        return serviceRepository.findById(id)
                .map(ServiceResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
    }

    public ServiceResponse updateService(String id, ServiceUpdateRequest request) {
        ServiceEntity entity = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));

        if (request.name() != null && !request.name().isBlank() && !request.name().equals(entity.getName())) {
            if (serviceRepository.existsByName(request.name())) {
                throw new IllegalArgumentException("Service with name '" + request.name() + "' already exists");
            }
            entity.setName(request.name());
        }

        if (request.pathPrefix() != null && !request.pathPrefix().isBlank()) {
            String normalizedPrefix = normalizePathPrefix(request.pathPrefix());
            if (!normalizedPrefix.equals(entity.getPathPrefix())) {
                if (serviceRepository.existsByPathPrefix(normalizedPrefix)) {
                    throw new IllegalArgumentException("Service with path prefix '" + normalizedPrefix + "' already exists");
                }
                entity.setPathPrefix(normalizedPrefix);
            }
        }

        if (request.baseUrl() != null && !request.baseUrl().isBlank()) {
            urlValidatorUtil.validate(request.baseUrl());
            entity.setBaseUrl(normalizeBaseUrl(request.baseUrl()));
        }

        if (request.connectionTimeoutMs() != null) {
            entity.setConnectionTimeoutMs(request.connectionTimeoutMs());
        }

        if (request.responseTimeoutMs() != null) {
            entity.setResponseTimeoutMs(request.responseTimeoutMs());
        }

        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }

        ServiceEntity updated = serviceRepository.save(entity);
        return ServiceResponse.fromEntity(updated);
    }

    public ServiceResponse toggleServiceStatus(String id, boolean enabled) {
        ServiceEntity entity = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));

        entity.setEnabled(enabled);
        ServiceEntity updated = serviceRepository.save(entity);
        return ServiceResponse.fromEntity(updated);
    }

    public void deleteService(String id) {
        ServiceEntity entity = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
        serviceRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public ServiceEntity getActiveServiceByPathPrefix(String pathPrefix) {
        String normalized = normalizePathPrefix(pathPrefix);
        ServiceEntity entity = serviceRepository.findByPathPrefix(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("No registered service found matching path prefix: " + normalized));

        if (!entity.isEnabled()) {
            throw new ServiceUnavailableException("Service '" + entity.getName() + "' is currently disabled");
        }

        return entity;
    }

    private String normalizePathPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("Path prefix must not be blank");
        }
        String p = prefix.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private String normalizeBaseUrl(String url) {
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
