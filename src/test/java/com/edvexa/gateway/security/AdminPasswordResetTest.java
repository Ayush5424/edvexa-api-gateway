package com.edvexa.gateway.security;

import com.edvexa.gateway.config.TestRedisConfig;
import com.edvexa.gateway.security.dto.LoginRequest;
import com.edvexa.gateway.security.dto.LoginResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminPasswordResetTest {

    @Autowired
    private AdminBootstrapService adminBootstrapService;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        adminUserRepository.deleteAll();
        AdminUserEntity admin = new AdminUserEntity(
                "admin",
                passwordEncoder.encode("InitialOldPassword123!"),
                "ROLE_ADMIN",
                true
        );
        adminUserRepository.save(admin);
    }

    @AfterEach
    void tearDown() {
        if (!adminUserRepository.existsByUsername("admin")) {
            adminUserRepository.save(new AdminUserEntity("admin", passwordEncoder.encode("AdminPass123!"), "ROLE_ADMIN", true));
        } else {
            AdminUserEntity admin = adminUserRepository.findByUsername("admin").orElseThrow();
            admin.setPasswordHash(passwordEncoder.encode("AdminPass123!"));
            adminUserRepository.save(admin);
        }
    }

    @Test
    @DisplayName("Should successfully reset password for existing administrator and allow login with new password")
    void shouldSuccessfullyResetPasswordForExistingAdmin() {
        String newPassword = "NewAdminPassword2026!";

        // Reset password
        adminBootstrapService.resetAdminPassword("admin", newPassword);

        // Login with new password must succeed
        LoginResponse response = authService.login(new LoginRequest("admin", newPassword));
        assertThat(response).isNotNull();
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.role()).isEqualTo("ROLE_ADMIN");
        assertThat(response.token()).isNotBlank();

        // Login with old password must fail
        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "InitialOldPassword123!")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    @DisplayName("Should fail safely when attempting password reset for nonexistent username without creating accounts")
    void shouldFailSafelyWhenResettingUnknownUsername() {
        long initialCount = adminUserRepository.count();

        assertThatThrownBy(() -> adminBootstrapService.resetAdminPassword("unknown_admin", "SomeSecurePass123!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Administrator account 'unknown_admin' not found in database");

        // Verify no new user was created
        assertThat(adminUserRepository.count()).isEqualTo(initialCount);
        assertThat(adminUserRepository.findByUsername("unknown_admin")).isEmpty();
    }

    @Test
    @DisplayName("Should hash new password using PasswordEncoder and never store plaintext")
    void shouldHashNewPasswordUsingPasswordEncoderAndNeverStorePlaintext() {
        String plaintextPassword = "PlaintextPasswordToVerify123!";

        adminBootstrapService.resetAdminPassword("admin", plaintextPassword);

        AdminUserEntity updatedUser = adminUserRepository.findByUsername("admin").orElseThrow();
        assertThat(updatedUser.getPasswordHash()).isNotEqualTo(plaintextPassword);
        assertThat(updatedUser.getPasswordHash()).startsWith("$2a$");
        assertThat(passwordEncoder.matches(plaintextPassword, updatedUser.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Should preserve existing administrator ID, username, role, enabled status, and createdAt metadata")
    void shouldPreserveAdminMetadataRoleAndEnabledStatus() {
        AdminUserEntity originalUser = adminUserRepository.findByUsername("admin").orElseThrow();
        Long originalId = originalUser.getId();
        String originalUsername = originalUser.getUsername();
        String originalRole = originalUser.getRole();
        boolean originalEnabled = originalUser.isEnabled();
        Instant originalCreatedAt = originalUser.getCreatedAt();

        adminBootstrapService.resetAdminPassword("admin", "UpdatedPasswordSecure123!");

        AdminUserEntity reloadedUser = adminUserRepository.findByUsername("admin").orElseThrow();
        assertThat(reloadedUser.getId()).isEqualTo(originalId);
        assertThat(reloadedUser.getUsername()).isEqualTo(originalUsername);
        assertThat(reloadedUser.getRole()).isEqualTo(originalRole);
        assertThat(reloadedUser.isEnabled()).isEqualTo(originalEnabled);
        assertThat(reloadedUser.getCreatedAt()).isEqualTo(originalCreatedAt);
    }
}
