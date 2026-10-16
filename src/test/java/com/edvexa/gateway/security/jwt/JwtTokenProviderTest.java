package com.edvexa.gateway.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(secret, 3600000L); // 1 hour
    }

    @Test
    void shouldGenerateAndValidateTokenSuccessfully() {
        String token = tokenProvider.generateToken("adminUser", "ROLE_ADMIN");

        assertThat(token).isNotBlank();
        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.getUsername(token)).isEqualTo("adminUser");
        assertThat(tokenProvider.getRole(token)).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = tokenProvider.generateToken("adminUser", "ROLE_ADMIN");
        String tamperedToken = token + "xyz";

        assertThat(tokenProvider.validateToken(tamperedToken)).isFalse();
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(secret, -1000L); // expired 1s ago
        String token = expiredProvider.generateToken("adminUser", "ROLE_ADMIN");

        assertThat(tokenProvider.validateToken(token)).isFalse();
    }

    @Test
    void shouldRejectNullOrBlankToken() {
        assertThat(tokenProvider.validateToken(null)).isFalse();
        assertThat(tokenProvider.validateToken("")).isFalse();
        assertThat(tokenProvider.validateToken("   ")).isFalse();
    }

    @Test
    void shouldFailWhenSecretIsMissingOrBlank() {
        assertThatThrownBy(() -> new JwtTokenProvider(null, 3600000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT signing secret is required");

        assertThatThrownBy(() -> new JwtTokenProvider("", 3600000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT signing secret is required");

        assertThatThrownBy(() -> new JwtTokenProvider("   ", 3600000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT signing secret is required");
    }

    @Test
    void shouldFailWhenSecretIsShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtTokenProvider("short-secret-under-32-bytes", 3600000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 256 bits (32 bytes)");
    }
}
