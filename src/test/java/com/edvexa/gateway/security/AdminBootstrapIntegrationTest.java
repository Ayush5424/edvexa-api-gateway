package com.edvexa.gateway.security;

import com.edvexa.gateway.config.TestRedisConfig;
import com.edvexa.gateway.security.dto.AdminBootstrapRequest;
import com.edvexa.gateway.security.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminBootstrapIntegrationTest {

    private static final String TEST_BOOTSTRAP_SECRET = "test-bootstrap-secret-value-12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        adminUserRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        if (!adminUserRepository.existsByUsername("admin")) {
            adminUserRepository.save(new AdminUserEntity("admin", passwordEncoder.encode("AdminPass123!"), "ROLE_ADMIN", true));
        }
    }

    @Test
    @DisplayName("Should successfully provision first administrator when valid secret is provided")
    void shouldSuccessfullyBootstrapFirstAdmin() throws Exception {
        AdminBootstrapRequest request = new AdminBootstrapRequest(
                TEST_BOOTSTRAP_SECRET,
                "superadmin",
                "SuperSecurePass2026!"
        );

        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", is("Administrator account provisioned successfully")))
                .andExpect(jsonPath("$.username", is("superadmin")))
                .andExpect(jsonPath("$.role", is("ROLE_ADMIN")));

        assertThat(adminUserRepository.count()).isEqualTo(1);
        AdminUserEntity entity = adminUserRepository.findByUsername("superadmin").orElseThrow();
        assertThat(entity.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(entity.isEnabled()).isTrue();

        // Verify login works immediately with the bootstrapped credentials
        LoginRequest loginRequest = new LoginRequest("superadmin", "SuperSecurePass2026!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.username", is("superadmin")))
                .andExpect(jsonPath("$.role", is("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("Should reject bootstrap request with incorrect bootstrap secret")
    void shouldRejectBootstrapWithInvalidSecret() throws Exception {
        AdminBootstrapRequest request = new AdminBootstrapRequest(
                "wrong-secret-value",
                "admin",
                "SecurePassword123!"
        );

        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Invalid bootstrap secret")));

        assertThat(adminUserRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should reject bootstrap when an administrator already exists")
    void shouldRejectBootstrapWhenAdminAlreadyExists() throws Exception {
        // Provision initial admin
        AdminBootstrapRequest initialRequest = new AdminBootstrapRequest(
                TEST_BOOTSTRAP_SECRET,
                "firstadmin",
                "FirstPassword123!"
        );
        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialRequest)))
                .andExpect(status().isCreated());

        // Subsequent bootstrap attempt must be rejected with 409 Conflict
        AdminBootstrapRequest secondRequest = new AdminBootstrapRequest(
                TEST_BOOTSTRAP_SECRET,
                "secondadmin",
                "SecondPassword123!"
        );
        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("An administrator account already exists. Bootstrap is disabled.")));

        assertThat(adminUserRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should validate required fields and password length constraints")
    void shouldValidateBootstrapRequestFields() throws Exception {
        // Too short password (< 8 chars)
        AdminBootstrapRequest shortPass = new AdminBootstrapRequest(
                TEST_BOOTSTRAP_SECRET,
                "admin",
                "short"
        );
        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortPass)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));

        // Blank secret
        AdminBootstrapRequest blankSecret = new AdminBootstrapRequest(
                "",
                "admin",
                "ValidPassword123!"
        );
        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankSecret)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should expose authentication system initialization status")
    void shouldExposeAuthStatus() throws Exception {
        // Initially empty
        mockMvc.perform(get("/api/v1/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialized", is(false)))
                .andExpect(jsonPath("$.bootstrapAllowed", is(true)));

        // Provision admin
        AdminBootstrapRequest request = new AdminBootstrapRequest(
                TEST_BOOTSTRAP_SECRET,
                "admin",
                "ValidPassword123!"
        );
        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Now initialized
        mockMvc.perform(get("/api/v1/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialized", is(true)))
                .andExpect(jsonPath("$.bootstrapAllowed", is(false)));
    }

    @Test
    @DisplayName("Should handle concurrent bootstrap requests safely with exactly one success")
    void shouldSafelyHandleConcurrentBootstrapAttempts() throws Exception {
        int threadCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            tasks.add(() -> {
                AdminBootstrapRequest request = new AdminBootstrapRequest(
                        TEST_BOOTSTRAP_SECRET,
                        "admin" + index,
                        "ThreadSecurePass" + index + "!"
                );
                try {
                    int statusCode = mockMvc.perform(post("/api/v1/auth/bootstrap")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andReturn()
                            .getResponse()
                            .getStatus();

                    if (statusCode == 201) {
                        successCount.incrementAndGet();
                    } else if (statusCode == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                }
                return null;
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);
        for (Future<Void> future : futures) {
            future.get();
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
        assertThat(adminUserRepository.count()).isEqualTo(1);
    }
}
