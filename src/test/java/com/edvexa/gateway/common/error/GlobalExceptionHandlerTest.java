package com.edvexa.gateway.common.error;

import com.edvexa.gateway.config.TestRedisConfig;
import com.edvexa.gateway.observability.CorrelationIdContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestRedisConfig.class, GlobalExceptionHandlerTest.TestExceptionController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class TestExceptionController {
        @GetMapping("/test/not-found")
        public void throwNotFound() {
            throw new ResourceNotFoundException("Target resource not found");
        }

        @GetMapping("/test/rate-limited")
        public void throwRateLimited() {
            throw new RateLimitExceededException("Rate limit of 60 req/min exceeded", 45);
        }

        @GetMapping("/test/conflict")
        public void throwConflict() {
            throw new IdempotencyConflictException("Concurrent request with same idempotency key in progress");
        }

        @GetMapping("/test/correlation")
        public String echoCorrelation() {
            return CorrelationIdContext.get();
        }
    }

    @Test
    void shouldReturn404ForResourceNotFoundException() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("Target resource not found")))
                .andExpect(jsonPath("$.correlationId", notNullValue()));
    }

    @Test
    void shouldReturn429WithRetryAfterHeaderForRateLimitExceeded() throws Exception {
        mockMvc.perform(get("/test/rate-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "45"))
                .andExpect(jsonPath("$.status", is(429)))
                .andExpect(jsonPath("$.error", is("Too Many Requests")))
                .andExpect(jsonPath("$.message", is("Rate limit of 60 req/min exceeded")));
    }

    @Test
    void shouldReturn409ForIdempotencyConflict() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")));
    }

    @Test
    void shouldPropagateSuppliedCorrelationId() throws Exception {
        String customId = "client-trace-12345";
        mockMvc.perform(get("/test/correlation")
                        .header("X-Correlation-ID", customId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", customId));
    }
}
