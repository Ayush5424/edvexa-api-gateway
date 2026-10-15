package com.edvexa.gateway.ratelimit;

import com.edvexa.gateway.common.error.ApiErrorResponse;
import com.edvexa.gateway.observability.CorrelationIdContext;
import com.edvexa.gateway.observability.RequestAuditService;
import com.edvexa.gateway.security.apikey.ApiKeyAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    public static final String HEADER_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    public static final String HEADER_RESET = "X-RateLimit-Reset";
    public static final String HEADER_RETRY_AFTER = "Retry-After";

    private final RateLimiterService rateLimiterService;
    private final RequestAuditService requestAuditService;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(
            RateLimiterService rateLimiterService,
            RequestAuditService requestAuditService,
            ObjectMapper objectMapper
    ) {
        this.rateLimiterService = rateLimiterService;
        this.requestAuditService = requestAuditService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/gateway");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String clientId = (String) request.getAttribute(ApiKeyAuthenticationFilter.ATTR_CLIENT_ID);
        Integer rateLimit = (Integer) request.getAttribute(ApiKeyAuthenticationFilter.ATTR_RATE_LIMIT);

        if (clientId == null || clientId.isBlank()) {
            clientId = "anonymous";
        }
        if (rateLimit == null || rateLimit <= 0) {
            rateLimit = 60;
        }

        RateLimitResult result = rateLimiterService.tryAcquire(clientId, rateLimit);

        response.setHeader(HEADER_LIMIT, String.valueOf(result.limit()));
        response.setHeader(HEADER_REMAINING, String.valueOf(result.remaining()));
        response.setHeader(HEADER_RESET, String.valueOf(result.resetSeconds()));

        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HEADER_RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            String correlationId = CorrelationIdContext.get();
            String errorMessage = "Rate limit exceeded. Quota of " + result.limit()
                    + " requests per minute reached. Try again in " + result.retryAfterSeconds() + " seconds";

            requestAuditService.recordRequest(
                    correlationId,
                    clientId,
                    null,
                    request.getMethod(),
                    request.getRequestURI(),
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    0L,
                    0,
                    "N/A",
                    errorMessage
            );

            ApiErrorResponse body = ApiErrorResponse.of(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                    errorMessage,
                    request.getRequestURI(),
                    correlationId
            );

            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
