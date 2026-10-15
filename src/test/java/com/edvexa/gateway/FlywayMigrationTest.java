package com.edvexa.gateway;

import com.edvexa.gateway.config.TestRedisConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldHaveAppliedInitialMigrationTables() {
        List<String> tables = jdbcTemplate.query(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_SCHEMA) = 'public'",
                (rs, rowNum) -> rs.getString("TABLE_NAME").toLowerCase()
        );

        assertThat(tables).contains(
                "services",
                "admin_users",
                "api_keys",
                "idempotency_records",
                "gateway_request_logs"
        );
    }
}
