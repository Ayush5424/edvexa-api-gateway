package com.edvexa.gateway.security.apikey;

import com.edvexa.gateway.common.error.ApiErrorResponse;
import com.edvexa.gateway.observability.CorrelationIdContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String ATTR_CLIENT_ID = "GATEWAY_CLIENT_ID";
    public static final String ATTR_CLIENT_NAME = "GATEWAY_CLIENT_NAME";
    public static final String ATTR_RATE_LIMIT = "GATEWAY_RATE_LIMIT";
    public static final String ATTR_API_KEY_ENTITY = "GATEWAY_API_KEY_ENTITY";

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyService;
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

        String rawKey = request.getHeader(API_KEY_HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "Missing API key in " + API_KEY_HEADER + " header", request.getRequestURI());
            return;
        }

        Optional<ApiKeyEntity> keyOpt = apiKeyService.validateApiKey(rawKey.trim());
        if (keyOpt.isEmpty()) {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "Invalid or revoked API key", request.getRequestURI());
            return;
        }

        ApiKeyEntity apiKey = keyOpt.get();

        request.setAttribute(ATTR_CLIENT_ID, apiKey.getClientName());
        request.setAttribute(ATTR_CLIENT_NAME, apiKey.getClientName());
        request.setAttribute(ATTR_RATE_LIMIT, apiKey.getRateLimitPerMinute());
        request.setAttribute(ATTR_API_KEY_ENTITY, apiKey);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                apiKey.getClientName(),
                rawKey,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus status, String message, String path)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                CorrelationIdContext.get()
        );
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
