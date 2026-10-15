package com.edvexa.gateway.registry;

import com.edvexa.gateway.config.TestRedisConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Transactional
class ServiceRegistryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ServiceRepository serviceRepository;

    @BeforeEach
    void setUp() {
        serviceRepository.deleteAll();
    }

    @Test
    void shouldRegisterValidService() throws Exception {
        Map<String, Object> request = Map.of(
                "name", "order-service",
                "baseUrl", "http://localhost:8081",
                "pathPrefix", "/orders",
                "connectionTimeoutMs", 3000,
                "responseTimeoutMs", 5000,
                "enabled", true
        );

        mockMvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("order-service")))
                .andExpect(jsonPath("$.baseUrl", is("http://localhost:8081")))
                .andExpect(jsonPath("$.pathPrefix", is("/orders")))
                .andExpect(jsonPath("$.enabled", is(true)));
    }

    @Test
    void shouldRejectInvalidServiceRegistration() throws Exception {
        Map<String, Object> request = Map.of(
                "name", "!", // invalid pattern
                "baseUrl", "not-a-valid-url",
                "pathPrefix", "missing-slash",
                "connectionTimeoutMs", 10 // below min
        );

        mockMvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", notNullValue()));
    }

    @Test
    void shouldRejectDuplicateRegistration() throws Exception {
        ServiceEntity existing = new ServiceEntity(
                "srv-1", "order-service", "http://localhost:8081", "/orders", 3000, 5000, true
        );
        serviceRepository.save(existing);

        Map<String, Object> duplicate = Map.of(
                "name", "order-service",
                "baseUrl", "http://localhost:8082",
                "pathPrefix", "/orders",
                "connectionTimeoutMs", 3000,
                "responseTimeoutMs", 5000,
                "enabled", true
        );

        mockMvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("already exists")));
    }

    @Test
    void shouldListAllRegisteredServices() throws Exception {
        ServiceEntity s1 = new ServiceEntity("s1", "service-1", "http://localhost:8081", "/s1", 2000, 4000, true);
        ServiceEntity s2 = new ServiceEntity("s2", "service-2", "http://localhost:8082", "/s2", 2000, 4000, true);
        serviceRepository.saveAll(java.util.List.of(s1, s2));

        mockMvc.perform(get("/api/v1/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void shouldGetRegisteredServiceById() throws Exception {
        ServiceEntity s = new ServiceEntity("s1", "service-1", "http://localhost:8081", "/s1", 2000, 4000, true);
        serviceRepository.save(s);

        mockMvc.perform(get("/api/v1/services/s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("service-1")));

        mockMvc.perform(get("/api/v1/services/non-existent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void shouldToggleServiceStatus() throws Exception {
        ServiceEntity s = new ServiceEntity("s1", "service-1", "http://localhost:8081", "/s1", 2000, 4000, true);
        serviceRepository.save(s);

        Map<String, Object> statusUpdate = Map.of("enabled", false);

        mockMvc.perform(patch("/api/v1/services/s1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)));
    }

    @Test
    void shouldDeleteService() throws Exception {
        ServiceEntity s = new ServiceEntity("s1", "service-1", "http://localhost:8081", "/s1", 2000, 4000, true);
        serviceRepository.save(s);

        mockMvc.perform(delete("/api/v1/services/s1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/services/s1"))
                .andExpect(status().isNotFound());
    }
}
