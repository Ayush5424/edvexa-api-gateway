package com.edvexa.gateway.security;

import com.edvexa.gateway.security.dto.AdminBootstrapRequest;
import com.edvexa.gateway.security.dto.AdminBootstrapResponse;
import com.edvexa.gateway.security.dto.LoginRequest;
import com.edvexa.gateway.security.dto.LoginResponse;
import com.edvexa.gateway.security.jwt.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Value("${gateway.security.bootstrap-secret:${GATEWAY_BOOTSTRAP_SECRET:}}")
    private String configuredBootstrapSecret;

    public AuthService(
            AdminUserRepository adminUserRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider
    ) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        AdminUserEntity user = adminUserRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Administrative account is disabled");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = tokenProvider.generateToken(user.getUsername(), user.getRole());
        return LoginResponse.of(token, tokenProvider.getExpirationMs(), user.getUsername(), user.getRole());
    }

    @Transactional
    public synchronized AdminBootstrapResponse bootstrapAdmin(AdminBootstrapRequest request) {
        if (configuredBootstrapSecret == null || configuredBootstrapSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrative bootstrap is disabled or bootstrap secret is not configured");
        }

        byte[] expectedSecret = configuredBootstrapSecret.trim().getBytes(StandardCharsets.UTF_8);
        byte[] providedSecret = request.bootstrapSecret().trim().getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedSecret, providedSecret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid bootstrap secret");
        }

        if (adminUserRepository.count() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An administrator account already exists. Bootstrap is disabled.");
        }

        if (adminUserRepository.existsByUsername(request.username().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }

        AdminUserEntity admin = new AdminUserEntity(
                request.username().trim(),
                passwordEncoder.encode(request.password()),
                "ROLE_ADMIN",
                true
        );

        try {
            adminUserRepository.saveAndFlush(admin);
            log.info("First administrative user successfully provisioned: {}", admin.getUsername());
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Administrator account already exists under concurrent provisioning");
        }

        return new AdminBootstrapResponse(
                "Administrator account provisioned successfully",
                admin.getUsername(),
                admin.getRole()
        );
    }

    @Transactional(readOnly = true)
    public boolean isAdminInitialized() {
        return adminUserRepository.count() > 0;
    }

    public boolean isBootstrapConfigured() {
        return configuredBootstrapSecret != null && !configuredBootstrapSecret.isBlank();
    }
}
