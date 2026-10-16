package com.edvexa.gateway.security;

import com.edvexa.gateway.security.dto.AdminBootstrapRequest;
import com.edvexa.gateway.security.dto.AdminBootstrapResponse;
import com.edvexa.gateway.security.dto.LoginRequest;
import com.edvexa.gateway.security.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Administrative authentication and JWT token management")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate administrator and obtain a signed JWT token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/bootstrap")
    @Operation(summary = "Provision the first administrator account using a secure bootstrap secret")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "First administrator account created"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "403", description = "Invalid bootstrap secret or bootstrap disabled"),
            @ApiResponse(responseCode = "409", description = "Administrator already exists")
    })
    public ResponseEntity<AdminBootstrapResponse> bootstrap(@Valid @RequestBody AdminBootstrapRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.bootstrapAdmin(request));
    }

    @GetMapping("/status")
    @Operation(summary = "Check authentication system initialization status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication initialization status")
    })
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean initialized = authService.isAdminInitialized();
        boolean bootstrapConfigured = authService.isBootstrapConfigured();
        return ResponseEntity.ok(Map.of(
                "initialized", initialized,
                "bootstrapAllowed", !initialized && bootstrapConfigured
        ));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated administrative user profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user profile"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(Map.of(
                "username", authentication.getName(),
                "authorities", authentication.getAuthorities().stream().map(Object::toString).toList()
        ));
    }
}
