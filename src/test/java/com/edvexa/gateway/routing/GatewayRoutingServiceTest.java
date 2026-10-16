package com.edvexa.gateway.routing;

import com.edvexa.gateway.idempotency.IdempotencyService;
import com.edvexa.gateway.observability.RequestAuditService;
import com.edvexa.gateway.registry.ServiceEntity;
import com.edvexa.gateway.registry.ServiceRepository;
import com.edvexa.gateway.resilience.CircuitBreakerService;
import com.edvexa.gateway.resilience.DownstreamClientProvider;
import com.edvexa.gateway.resilience.RetryPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayRoutingServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private HopByHopHeaderFilter headerFilter;

    @Mock
    private DownstreamClientProvider clientProvider;

    @Mock
    private CircuitBreakerService circuitBreakerService;

    @Mock
    private RetryPolicyService retryPolicyService;

    @Mock
    private RequestAuditService requestAuditService;

    @Mock
    private IdempotencyService idempotencyService;

    private GatewayRoutingService routingService;

    @BeforeEach
    void setUp() {
        routingService = new GatewayRoutingService(
                serviceRepository,
                headerFilter,
                clientProvider,
                circuitBreakerService,
                retryPolicyService,
                requestAuditService,
                idempotencyService
        );
    }

    @Test
    @DisplayName("Should resolve registered /test service when subpath is /test")
    void shouldResolveRegisteredServiceWithNormalizedPrefix() {
        ServiceEntity testService = new ServiceEntity(
                "srv-test",
                "test-service",
                "http://localhost:8081",
                "/test",
                2000,
                5000,
                true
        );
        when(serviceRepository.findAll()).thenReturn(List.of(testService));

        ServiceEntity resolved = routingService.resolveService("/test");
        assertThat(resolved).isNotNull();
        assertThat(resolved.getName()).isEqualTo("test-service");
        assertThat(resolved.getPathPrefix()).isEqualTo("/test");
    }

    @Test
    @DisplayName("Should handle trailing slash in subpath or prefix gracefully")
    void shouldHandleTrailingSlashInSubpathOrPrefix() {
        ServiceEntity testService = new ServiceEntity(
                "srv-test",
                "test-service",
                "http://localhost:8081",
                "/test",
                2000,
                5000,
                true
        );
        when(serviceRepository.findAll()).thenReturn(List.of(testService));

        // Request with trailing slash
        ServiceEntity resolvedWithTrailingSlash = routingService.resolveService("/test/");
        assertThat(resolvedWithTrailingSlash).isNotNull();
        assertThat(resolvedWithTrailingSlash.getName()).isEqualTo("test-service");
    }

    @Test
    @DisplayName("Should select more-specific prefix over broader prefix")
    void shouldSelectMoreSpecificPrefixOverBroaderPrefix() {
        ServiceEntity broader = new ServiceEntity(
                "srv-api",
                "api-service",
                "http://localhost:8082",
                "/api",
                2000,
                5000,
                true
        );
        ServiceEntity specific = new ServiceEntity(
                "srv-orders",
                "orders-service",
                "http://localhost:8083",
                "/api/v1/orders",
                2000,
                5000,
                true
        );
        when(serviceRepository.findAll()).thenReturn(List.of(broader, specific));

        // Subpath matches both, specific must win
        ServiceEntity resolved = routingService.resolveService("/api/v1/orders/123");
        assertThat(resolved).isNotNull();
        assertThat(resolved.getName()).isEqualTo("orders-service");

        // Subpath only matches broader
        ServiceEntity resolvedBroader = routingService.resolveService("/api/v1/users");
        assertThat(resolvedBroader).isNotNull();
        assertThat(resolvedBroader.getName()).isEqualTo("api-service");
    }

    @Test
    @DisplayName("Should not match partial segment names as prefixes")
    void shouldNotMatchPartialSegmentNames() {
        ServiceEntity testService = new ServiceEntity(
                "srv-test",
                "test-service",
                "http://localhost:8081",
                "/test",
                2000,
                5000,
                true
        );
        when(serviceRepository.findAll()).thenReturn(List.of(testService));

        // /testing and /test-user must not match /test
        assertThat(routingService.resolveService("/testing")).isNull();
        assertThat(routingService.resolveService("/test-service")).isNull();
    }

    @Test
    @DisplayName("Should construct downstream URI correctly when target base URL does not include prefix")
    void shouldConstructDownstreamUriWhenBaseUrlDoesNotIncludePrefix() {
        ServiceEntity testService = new ServiceEntity(
                "srv-test",
                "test-service",
                "http://localhost:8081",
                "/test",
                2000,
                5000,
                true
        );

        URI uri1 = routingService.buildDownstreamUri(testService, "/test", null);
        assertThat(uri1).hasToString("http://localhost:8081/test");

        URI uri2 = routingService.buildDownstreamUri(testService, "/test/status", "detail=true");
        assertThat(uri2).hasToString("http://localhost:8081/test/status?detail=true");
    }

    @Test
    @DisplayName("Should construct downstream URI correctly when target base URL already ends with prefix")
    void shouldConstructDownstreamUriWhenBaseUrlAlreadyEndsWithPrefix() {
        ServiceEntity orderService = new ServiceEntity(
                "srv-order",
                "order-service",
                "http://localhost:8080/api/v1/simulated/orders",
                "/orders",
                2000,
                5000,
                true
        );

        URI uri1 = routingService.buildDownstreamUri(orderService, "/orders", null);
        assertThat(uri1).hasToString("http://localhost:8080/api/v1/simulated/orders");

        URI uri2 = routingService.buildDownstreamUri(orderService, "/orders/100", "fields=all");
        assertThat(uri2).hasToString("http://localhost:8080/api/v1/simulated/orders/100?fields=all");
    }
}
