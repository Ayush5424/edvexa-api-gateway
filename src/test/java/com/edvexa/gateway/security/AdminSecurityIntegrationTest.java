package com.edvexa.gateway.security;

import com.edvexa.gateway.config.TestRedisConfig;
import com.edvexa.gateway.security.dto.LoginRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class AdminSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRejectUnauthenticatedRequestToServiceRegistry() throws Exception {
        mockMvc.perform(get("/api/v1/services"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Full authentication is required to access this resource")));
    }

    @Test
    void shouldRejectLoginWithInvalidCredentials() throws Exception {
        LoginRequest badLogin = new LoginRequest("admin", "WrongPassword!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Invalid username or password")));
    }

    @Test
    void shouldAuthenticateAdminAndGrantAccessWithJwtToken() throws Exception {
        // 1. Login with bootstrap credentials
        LoginRequest loginRequest = new LoginRequest("admin", "AdminPass123!");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.username", is("admin")))
                .andExpect(jsonPath("$.role", is("ROLE_ADMIN")))
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String jwtToken = responseNode.get("token").asText();

        // 2. Access protected service registry with valid JWT Bearer token
        mockMvc.perform(get("/api/v1/services")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());

        // 3. Verify user profile endpoint
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("admin")));
    }

    @Test
    void shouldRejectTamperedJwtToken() throws Exception {
        mockMvc.perform(get("/api/v1/services")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.invalid.signature"))
                .andExpect(status().isUnauthorized());
    }
}
