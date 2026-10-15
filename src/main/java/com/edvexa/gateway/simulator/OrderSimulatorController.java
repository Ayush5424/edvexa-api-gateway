package com.edvexa.gateway.simulator;

import com.edvexa.gateway.simulator.dto.SimulatorConfigDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/simulated/orders")
@Tag(name = "Order Simulator", description = "Simulated downstream order service and failure injection controls")
public class OrderSimulatorController {

    private final OrderSimulatorService orderSimulatorService;

    public OrderSimulatorController(OrderSimulatorService orderSimulatorService) {
        this.orderSimulatorService = orderSimulatorService;
    }

    @GetMapping
    @Operation(summary = "List simulated orders")
    public ResponseEntity<List<Map<String, Object>>> listOrders() {
        return ResponseEntity.ok(orderSimulatorService.listOrders());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get simulated order by ID")
    public ResponseEntity<Map<String, Object>> getOrderById(@PathVariable String id) {
        return ResponseEntity.ok(orderSimulatorService.getOrderById(id));
    }

    @PostMapping
    @Operation(summary = "Create a simulated order")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> payload) {
        Map<String, Object> created = orderSimulatorService.createOrder(payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/config")
    @Operation(summary = "Get current order service simulator configuration and metrics")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(orderSimulatorService.getSimulatorState().getStatus());
    }

    @PostMapping("/config")
    @Operation(summary = "Update order service simulator failure rate, latency, and deterministic status controls")
    public ResponseEntity<Map<String, Object>> updateConfig(@Valid @RequestBody SimulatorConfigDto config) {
        orderSimulatorService.updateConfig(config);
        return ResponseEntity.ok(orderSimulatorService.getSimulatorState().getStatus());
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset order service simulator state and failure controls")
    public ResponseEntity<Map<String, Object>> reset() {
        orderSimulatorService.reset();
        return ResponseEntity.ok(orderSimulatorService.getSimulatorState().getStatus());
    }
}
