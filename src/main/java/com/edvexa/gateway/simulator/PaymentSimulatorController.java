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

import java.util.Map;

@RestController
@RequestMapping("/api/v1/simulated/payments")
@Tag(name = "Payment Simulator", description = "Simulated downstream payment service and failure injection controls")
public class PaymentSimulatorController {

    private final PaymentSimulatorService paymentSimulatorService;

    public PaymentSimulatorController(PaymentSimulatorService paymentSimulatorService) {
        this.paymentSimulatorService = paymentSimulatorService;
    }

    @PostMapping("/charge")
    @Operation(summary = "Process a simulated payment charge")
    public ResponseEntity<Map<String, Object>> processPayment(@RequestBody Map<String, Object> payload) {
        Map<String, Object> processed = paymentSimulatorService.processPayment(payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(processed);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get simulated payment transaction by ID")
    public ResponseEntity<Map<String, Object>> getPaymentById(@PathVariable String id) {
        return ResponseEntity.ok(paymentSimulatorService.getPaymentById(id));
    }

    @GetMapping("/config")
    @Operation(summary = "Get current payment service simulator configuration and metrics")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(paymentSimulatorService.getSimulatorState().getStatus());
    }

    @PostMapping("/config")
    @Operation(summary = "Update payment service simulator failure rate, latency, and deterministic status controls")
    public ResponseEntity<Map<String, Object>> updateConfig(@Valid @RequestBody SimulatorConfigDto config) {
        paymentSimulatorService.updateConfig(config);
        return ResponseEntity.ok(paymentSimulatorService.getSimulatorState().getStatus());
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset payment service simulator state and failure controls")
    public ResponseEntity<Map<String, Object>> reset() {
        paymentSimulatorService.reset();
        return ResponseEntity.ok(paymentSimulatorService.getSimulatorState().getStatus());
    }
}
