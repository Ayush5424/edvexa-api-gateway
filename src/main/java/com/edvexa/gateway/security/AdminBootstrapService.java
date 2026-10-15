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

    @Value("${gateway.security.admin.initial-username:admin}")
    private String initialUsername;

    @Value("${gateway.security.admin.initial-password:AdminPass123!}")
    private String initialPassword;

    public AdminBootstrapService(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminUserRepository.count() == 0) {
            log.info("No administrative accounts found in database. Initializing bootstrap admin account: {}", initialUsername);
            AdminUserEntity admin = new AdminUserEntity(
                    initialUsername,
                    passwordEncoder.encode(initialPassword),
                    "ROLE_ADMIN",
                    true
            );
            adminUserRepository.save(admin);
            log.info("Bootstrap administrative account initialized successfully.");
        }
    }
}
