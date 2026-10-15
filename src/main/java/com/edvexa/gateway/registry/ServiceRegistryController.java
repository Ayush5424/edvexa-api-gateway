package com.edvexa.gateway.registry;

import com.edvexa.gateway.registry.dto.ServiceRegistrationRequest;
import com.edvexa.gateway.registry.dto.ServiceResponse;
import com.edvexa.gateway.registry.dto.ServiceStatusUpdateRequest;
import com.edvexa.gateway.registry.dto.ServiceUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/services")
@Tag(name = "Service Registry", description = "Administrative APIs for managing downstream service registrations")
public class ServiceRegistryController {

    private final ServiceRegistryService serviceRegistryService;

    public ServiceRegistryController(ServiceRegistryService serviceRegistryService) {
        this.serviceRegistryService = serviceRegistryService;
    }

    @PostMapping
    @Operation(summary = "Register a new downstream service")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Service registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or validation failure"),
            @ApiResponse(responseCode = "409", description = "Service name or path prefix conflict")
    })
    public ResponseEntity<ServiceResponse> registerService(@Valid @RequestBody ServiceRegistrationRequest request) {
        ServiceResponse response = serviceRegistryService.registerService(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List all registered downstream services")
    public ResponseEntity<List<ServiceResponse>> getAllServices() {
        return ResponseEntity.ok(serviceRegistryService.getAllServices());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve registered service by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service found"),
            @ApiResponse(responseCode = "404", description = "Service not found")
    })
    public ResponseEntity<ServiceResponse> getServiceById(@PathVariable String id) {
        return ResponseEntity.ok(serviceRegistryService.getServiceById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update service configuration")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid configuration data"),
            @ApiResponse(responseCode = "404", description = "Service not found")
    })
    public ResponseEntity<ServiceResponse> updateService(
            @PathVariable String id,
            @Valid @RequestBody ServiceUpdateRequest request
    ) {
        return ResponseEntity.ok(serviceRegistryService.updateService(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Enable or disable a registered service")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service status toggled successfully"),
            @ApiResponse(responseCode = "404", description = "Service not found")
    })
    public ResponseEntity<ServiceResponse> toggleServiceStatus(
            @PathVariable String id,
            @Valid @RequestBody ServiceStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(serviceRegistryService.toggleServiceStatus(id, request.enabled()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Safely remove a registered service")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Service removed successfully"),
            @ApiResponse(responseCode = "404", description = "Service not found")
    })
    public ResponseEntity<Void> deleteService(@PathVariable String id) {
        serviceRegistryService.deleteService(id);
        return ResponseEntity.noContent().build();
    }
}
