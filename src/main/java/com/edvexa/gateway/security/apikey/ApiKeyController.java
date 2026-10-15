package com.edvexa.gateway.security.apikey;

import com.edvexa.gateway.security.apikey.dto.ApiKeyCreatedResponse;
import com.edvexa.gateway.security.apikey.dto.ApiKeyResponse;
import com.edvexa.gateway.security.apikey.dto.CreateApiKeyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/api-keys")
@Tag(name = "API Key Management", description = "Administrative endpoints for issuing, listing, and revoking client API keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @Operation(summary = "Generate a new client API key", description = "Issues a new cryptographically secure API key. The raw key is returned exactly once.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "API key created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - requires admin JWT")
    })
    public ResponseEntity<ApiKeyCreatedResponse> createApiKey(@Valid @RequestBody CreateApiKeyRequest request) {
        ApiKeyCreatedResponse response = apiKeyService.createApiKey(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all client API keys", description = "Returns all API keys without sensitive raw secret material")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of API keys retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - requires admin JWT")
    })
    public ResponseEntity<List<ApiKeyResponse>> listApiKeys() {
        return ResponseEntity.ok(apiKeyService.listApiKeys());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get API key details", description = "Returns metadata for a specific API key")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "API key found"),
            @ApiResponse(responseCode = "404", description = "API key not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - requires admin JWT")
    })
    public ResponseEntity<ApiKeyResponse> getApiKey(@PathVariable String id) {
        return ResponseEntity.ok(apiKeyService.getApiKey(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke an API key", description = "Marks the API key as REVOKED and prevents further gateway access")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "API key revoked successfully"),
            @ApiResponse(responseCode = "404", description = "API key not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - requires admin JWT")
    })
    public ResponseEntity<ApiKeyResponse> revokeApiKey(@PathVariable String id) {
        return ResponseEntity.ok(apiKeyService.revokeApiKey(id));
    }
}
