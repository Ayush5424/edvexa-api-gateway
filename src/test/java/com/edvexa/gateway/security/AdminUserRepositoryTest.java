package com.edvexa.gateway.security;

import com.edvexa.gateway.config.TestRedisConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Transactional
class AdminUserRepositoryTest {

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        adminUserRepository.deleteAll();
    }

    @Test
    void shouldPersistAndFindAdminUser() {
        String encoded = passwordEncoder.encode("SecretPass123");
        AdminUserEntity admin = new AdminUserEntity("secadmin", encoded, "ROLE_ADMIN", true);

        AdminUserEntity saved = adminUserRepository.save(admin);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<AdminUserEntity> found = adminUserRepository.findByUsername("secadmin");
        assertThat(found).isPresent();
        assertThat(passwordEncoder.matches("SecretPass123", found.get().getPasswordHash())).isTrue();
        assertThat(found.get().getRole()).isEqualTo("ROLE_ADMIN");

        assertThat(adminUserRepository.existsByUsername("secadmin")).isTrue();
        assertThat(adminUserRepository.existsByUsername("nobody")).isFalse();
    }
}
