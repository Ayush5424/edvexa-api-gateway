package com.edvexa.gateway.registry;

import com.edvexa.gateway.common.error.ResourceNotFoundException;
import com.edvexa.gateway.common.error.ServiceUnavailableException;
import com.edvexa.gateway.registry.dto.ServiceRegistrationRequest;
import com.edvexa.gateway.registry.dto.ServiceResponse;
import com.edvexa.gateway.registry.dto.ServiceUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceRegistryServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Spy
    private UrlValidatorUtil urlValidatorUtil = new UrlValidatorUtil(true);

    @InjectMocks
    private ServiceRegistryService serviceRegistryService;

    private ServiceRegistrationRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new ServiceRegistrationRequest(
                "order-service",
                "http://localhost:8081",
                "/orders",
                3000,
                5000,
                true
        );
    }

    @Test
    void shouldRegisterServiceSuccessfully() {
        when(serviceRepository.existsByName("order-service")).thenReturn(false);
        when(serviceRepository.existsByPathPrefix("/orders")).thenReturn(false);

        ServiceEntity savedEntity = new ServiceEntity(
                "srv-1", "order-service", "http://localhost:8081", "/orders", 3000, 5000, true
        );
        when(serviceRepository.save(any(ServiceEntity.class))).thenReturn(savedEntity);

        ServiceResponse response = serviceRegistryService.registerService(validRequest);

        assertThat(response.id()).isEqualTo("srv-1");
        assertThat(response.name()).isEqualTo("order-service");
        assertThat(response.pathPrefix()).isEqualTo("/orders");
        assertThat(response.baseUrl()).isEqualTo("http://localhost:8081");
        verify(serviceRepository).save(any(ServiceEntity.class));
    }

    @Test
    void shouldRejectDuplicateServiceName() {
        when(serviceRepository.existsByName("order-service")).thenReturn(true);

        assertThatThrownBy(() -> serviceRegistryService.registerService(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void shouldRejectDuplicatePathPrefix() {
        when(serviceRepository.existsByName("order-service")).thenReturn(false);
        when(serviceRepository.existsByPathPrefix("/orders")).thenReturn(true);

        assertThatThrownBy(() -> serviceRegistryService.registerService(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void shouldRejectSsrfCloudMetadataAddress() {
        when(serviceRepository.existsByName("order-service")).thenReturn(false);
        when(serviceRepository.existsByPathPrefix("/orders")).thenReturn(false);

        ServiceRegistrationRequest badRequest = new ServiceRegistrationRequest(
                "order-service",
                "http://169.254.169.254/latest/meta-data",
                "/orders",
                3000,
                5000,
                true
        );

        assertThatThrownBy(() -> serviceRegistryService.registerService(badRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forbidden cloud metadata");
    }

    @Test
    void shouldRejectUnsupportedScheme() {
        when(serviceRepository.existsByName("order-service")).thenReturn(false);
        when(serviceRepository.existsByPathPrefix("/orders")).thenReturn(false);

        ServiceRegistrationRequest badRequest = new ServiceRegistrationRequest(
                "order-service",
                "ftp://fileserver/orders",
                "/orders",
                3000,
                5000,
                true
        );

        assertThatThrownBy(() -> serviceRegistryService.registerService(badRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only HTTP and HTTPS are permitted");
    }

    @Test
    void shouldThrowWhenServiceNotFoundById() {
        when(serviceRepository.findById("unknown-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceRegistryService.getServiceById("unknown-id"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Service not found with id: unknown-id");
    }

    @Test
    void shouldToggleServiceStatus() {
        ServiceEntity entity = new ServiceEntity(
                "srv-1", "order-service", "http://localhost:8081", "/orders", 3000, 5000, true
        );
        when(serviceRepository.findById("srv-1")).thenReturn(Optional.of(entity));
        when(serviceRepository.save(entity)).thenReturn(entity);

        ServiceResponse response = serviceRegistryService.toggleServiceStatus("srv-1", false);
        assertThat(response.enabled()).isFalse();
    }

    @Test
    void shouldThrowServiceUnavailableWhenAccessingDisabledService() {
        ServiceEntity entity = new ServiceEntity(
                "srv-1", "order-service", "http://localhost:8081", "/orders", 3000, 5000, false
        );
        when(serviceRepository.findByPathPrefix("/orders")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> serviceRegistryService.getActiveServiceByPathPrefix("/orders"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("is currently disabled");
    }
}
