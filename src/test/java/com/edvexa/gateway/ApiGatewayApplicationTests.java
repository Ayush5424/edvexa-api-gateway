package com.edvexa.gateway;

import com.edvexa.gateway.config.TestRedisConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
        // Verifies complete ApplicationContext starts with H2, Flyway migrations, and security configuration
    }
}
