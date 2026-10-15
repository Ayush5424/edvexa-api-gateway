package com.edvexa.gateway.registry;

import com.edvexa.gateway.config.TestRedisConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class ServiceRepositoryTest {

    @Autowired
    private ServiceRepository serviceRepository;

    @BeforeEach
    void setUp() {
        serviceRepository.deleteAll();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        serviceRepository.deleteAll();
    }

    @Test
    void shouldPersistAndRetrieveService() {
        ServiceEntity service = new ServiceEntity(
                "srv-order-001",
                "order-service",
                "http://localhost:8081",
                "/orders",
                3000,
                5000,
                true
        );

        ServiceEntity saved = serviceRepository.save(service);

        assertThat(saved.getId()).isEqualTo("srv-order-001");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<ServiceEntity> byName = serviceRepository.findByName("order-service");
        assertThat(byName).isPresent();
        assertThat(byName.get().getBaseUrl()).isEqualTo("http://localhost:8081");

        Optional<ServiceEntity> byPrefix = serviceRepository.findByPathPrefix("/orders");
        assertThat(byPrefix).isPresent();
        assertThat(byPrefix.get().getName()).isEqualTo("order-service");

        assertThat(serviceRepository.existsByName("order-service")).isTrue();
        assertThat(serviceRepository.existsByPathPrefix("/orders")).isTrue();
        assertThat(serviceRepository.existsByName("non-existent")).isFalse();
    }

    @Test
    void shouldFilterEnabledServices() {
        ServiceEntity active = new ServiceEntity("srv-1", "service-1", "http://localhost:8081", "/s1", 2000, 4000, true);
        ServiceEntity inactive = new ServiceEntity("srv-2", "service-2", "http://localhost:8082", "/s2", 2000, 4000, false);

        serviceRepository.saveAll(List.of(active, inactive));

        List<ServiceEntity> enabled = serviceRepository.findByEnabledTrue();
        assertThat(enabled).hasSize(1);
        assertThat(enabled.get(0).getName()).isEqualTo("service-1");
    }
}
