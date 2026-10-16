package com.edvexa.gateway.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdTest {

    @AfterEach
    void tearDown() {
        CorrelationIdContext.clear();
    }

    @Test
    @DisplayName("getOrGenerate preserves valid incoming correlation ID")
    void testValidIncomingId() {
        String validId = "req-12345_abc-XYZ";
        String result = CorrelationIdContext.getOrGenerate(validId);
        assertThat(result).isEqualTo(validId);
    }

    @Test
    @DisplayName("getOrGenerate generates new UUID when incoming ID is null or blank")
    void testNullOrBlankGeneratesUuid() {
        String resultNull = CorrelationIdContext.getOrGenerate(null);
        String resultEmpty = CorrelationIdContext.getOrGenerate("");
        String resultSpaces = CorrelationIdContext.getOrGenerate("   ");

        assertThat(resultNull).isNotBlank();
        assertThat(resultEmpty).isNotBlank().isNotEqualTo(resultNull);
        assertThat(resultSpaces).isNotBlank();
    }

    @Test
    @DisplayName("getOrGenerate replaces malformed or unbounded correlation IDs with UUID")
    void testMalformedOrUnboundedReplacedWithUuid() {
        String idWithSpaces = "req with spaces";
        String idWithScript = "<script>alert(1)</script>";
        String idWithNewlines = "id\r\ninjection";
        String idTooLong = "a".repeat(65);

        assertThat(CorrelationIdContext.getOrGenerate(idWithSpaces)).doesNotContain(" ");
        assertThat(CorrelationIdContext.getOrGenerate(idWithScript)).doesNotContain("<script>");
        assertThat(CorrelationIdContext.getOrGenerate(idWithNewlines)).doesNotContain("\n");
        assertThat(CorrelationIdContext.getOrGenerate(idTooLong)).hasSize(36); // UUID size
    }

    @Test
    @DisplayName("set, get, and clear correctly manage MDC context")
    void testMdcContextLifecycle() {
        CorrelationIdContext.set("corr-test-99");
        assertThat(CorrelationIdContext.get()).isEqualTo("corr-test-99");

        CorrelationIdContext.clear();
        assertThat(CorrelationIdContext.get()).isEmpty();
    }
}
