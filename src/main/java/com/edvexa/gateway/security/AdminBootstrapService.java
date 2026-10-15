package com.edvexa.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${gateway.security.admin.initial-username:${GATEWAY_ADMIN_INITIAL_USERNAME:admin}}")
    private String initialUsername;

    @Value("${gateway.security.admin.initial-password:${GATEWAY_ADMIN_INITIAL_PASSWORD:}}")
    private String initialPassword;

    @Value("${gateway.security.admin.reset-password:${GATEWAY_ADMIN_RESET_PASSWORD:}}")
    private String resetPassword;

    public AdminBootstrapService(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (resetPassword != null && !resetPassword.isBlank()) {
            try {
                resetAdminPassword(initialUsername, resetPassword);
            } catch (IllegalArgumentException ex) {
                log.warn("Administrator password reset could not be completed: {}", ex.getMessage());
            }
            return;
        }

        if (adminUserRepository.count() == 0) {
            if (initialPassword != null && !initialPassword.isBlank()) {
                log.info("No administrative accounts found in database. Initializing bootstrap admin account from environment configuration: {}", initialUsername);
                AdminUserEntity admin = new AdminUserEntity(
                        initialUsername,
                        passwordEncoder.encode(initialPassword),
                        "ROLE_ADMIN",
                        true
                );
                adminUserRepository.save(admin);
                log.info("Bootstrap administrative account initialized successfully.");
            } else {
                log.info("No administrative accounts found in database. First administrator must be provisioned via POST /api/v1/auth/bootstrap with the configured bootstrap secret.");
            }
        }
    }

    @Transactional
    public void resetAdminPassword(String username, String newPassword) {
        String targetUsername = username != null && !username.isBlank() ? username.trim() : "admin";
        log.info("Explicit administrator password reset requested for username: {}", targetUsername);

        AdminUserEntity existingUser = adminUserRepository.findByUsername(targetUsername)
                .orElseThrow(() -> {
                    log.error("Administrator password reset failed: account '{}' not found in database. No administrator account created.", targetUsername);
                    return new IllegalArgumentException("Administrator account '" + targetUsername + "' not found in database");
                });

        existingUser.setPasswordHash(passwordEncoder.encode(newPassword));
        adminUserRepository.save(existingUser);
        log.info("Administrator password successfully updated for account '{}'. Existing role, status, and metadata preserved.", existingUser.getUsername());
    }
}
