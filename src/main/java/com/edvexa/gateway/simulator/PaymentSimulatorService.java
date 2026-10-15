package com.edvexa.gateway.simulator;

import com.edvexa.gateway.common.error.ResourceNotFoundException;
import com.edvexa.gateway.simulator.dto.SimulatorConfigDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentSimulatorService {

    private final SimulatorState simulatorState = new SimulatorState("PaymentService");
    private final Map<String, Map<String, Object>> payments = new ConcurrentHashMap<>();

    public PaymentSimulatorService() {
        savePayment("pay-101", 149.99, "USD", "COMPLETED", "card_visa_1234");
    }

    private void savePayment(String id, double amount, String currency, String status, String paymentMethod) {
        payments.put(id, Map.of(
                "id", id,
                "amount", amount,
                "currency", currency,
                "status", status,
                "paymentMethod", paymentMethod,
                "processedAt", Instant.now().toString()
        ));
    }

    public Map<String, Object> processPayment(Map<String, Object> payload) {
        simulatorState.executeSimulation();

        String id = "pay-" + UUID.randomUUID().toString().substring(0, 8);
        double amount = payload.containsKey("amount") ? Double.parseDouble(payload.get("amount").toString()) : 50.00;
        String currency = payload.getOrDefault("currency", "USD").toString();
        String paymentMethod = payload.getOrDefault("paymentMethod", "card_default").toString();

        Map<String, Object> payment = Map.of(
                "id", id,
                "amount", amount,
                "currency", currency,
                "status", "SUCCEEDED",
                "paymentMethod", paymentMethod,
                "processedAt", Instant.now().toString()
        );
        payments.put(id, payment);
        return payment;
    }

    public Map<String, Object> getPaymentById(String paymentId) {
        simulatorState.executeSimulation();
        Map<String, Object> payment = payments.get(paymentId);
        if (payment == null) {
            throw new ResourceNotFoundException("Payment not found with id: " + paymentId);
        }
        return payment;
    }

    public SimulatorState getSimulatorState() {
        return simulatorState;
    }

    public void updateConfig(SimulatorConfigDto config) {
        simulatorState.updateConfig(config);
    }

    public void reset() {
        simulatorState.reset();
    }
}
