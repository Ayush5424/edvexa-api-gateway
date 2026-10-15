package com.edvexa.gateway.routing;

import com.edvexa.gateway.observability.CorrelationIdContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.Collections;
import java.util.List;
import java.util.Vector;

import static org.assertj.core.api.Assertions.assertThat;

class HopByHopHeaderFilterTest {

    private HopByHopHeaderFilter filter;

    @BeforeEach
    void setUp() {
        filter = new HopByHopHeaderFilter();
        CorrelationIdContext.set("trace-test-abc");
    }

    @AfterEach
    void tearDown() {
        CorrelationIdContext.clear();
    }

    @Test
    void shouldStripHopByHopHeadersFromRequest() {
        Vector<String> names = new Vector<>(List.of(
                "Connection",
                "Keep-Alive",
                "Host",
                "Content-Length",
                "Transfer-Encoding",
                "Upgrade",
                "Accept",
                "X-Custom-Header"
        ));

        HttpHeaders clean = filter.filterHeaders(names.elements(), name -> {
            if ("Accept".equalsIgnoreCase(name)) return new Vector<>(List.of("application/json")).elements();
            if ("X-Custom-Header".equalsIgnoreCase(name)) return new Vector<>(List.of("custom-val")).elements();
            return new Vector<>(List.of("discard")).elements();
        });

        assertThat(clean.containsKey("Connection")).isFalse();
        assertThat(clean.containsKey("Keep-Alive")).isFalse();
        assertThat(clean.containsKey("Host")).isFalse();
        assertThat(clean.containsKey("Content-Length")).isFalse();
        assertThat(clean.containsKey("Transfer-Encoding")).isFalse();
        assertThat(clean.containsKey("Upgrade")).isFalse();

        assertThat(clean.getFirst("Accept")).isEqualTo("application/json");
        assertThat(clean.getFirst("X-Custom-Header")).isEqualTo("custom-val");
        assertThat(clean.getFirst("X-Correlation-ID")).isEqualTo("trace-test-abc");
    }

    @Test
    void shouldStripHopByHopHeadersFromResponse() {
        HttpHeaders downstream = new HttpHeaders();
        downstream.add("Connection", "close");
        downstream.add("Transfer-Encoding", "chunked");
        downstream.add("Content-Type", "application/json");
        downstream.add("X-Service-Version", "v1.0.0");

        HttpHeaders clean = filter.filterDownstreamResponseHeaders(downstream);

        assertThat(clean.containsKey("Connection")).isFalse();
        assertThat(clean.containsKey("Transfer-Encoding")).isFalse();
        assertThat(clean.getFirst("Content-Type")).isEqualTo("application/json");
        assertThat(clean.getFirst("X-Service-Version")).isEqualTo("v1.0.0");
        assertThat(clean.getFirst("X-Correlation-ID")).isEqualTo("trace-test-abc");
    }
}
