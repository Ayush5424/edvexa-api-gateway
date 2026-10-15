package com.edvexa.gateway.simulator;

import com.edvexa.gateway.common.error.ResourceNotFoundException;
import com.edvexa.gateway.simulator.dto.SimulatorConfigDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderSimulatorService {

    private final SimulatorState simulatorState = new SimulatorState("OrderService");
    private final Map<String, Map<String, Object>> orders = new ConcurrentHashMap<>();

    public OrderSimulatorService() {
        // Pre-populate with sample orders for evaluation
        saveOrder("ord-101", "ORD-101", 149.99, "CONFIRMED", "cust-01");
        saveOrder("ord-102", "ORD-102", 89.50, "SHIPPED", "cust-02");
    }

    private void saveOrder(String id, String orderNumber, double amount, String status, String customerId) {
        orders.put(id, Map.of(
                "id", id,
                "orderNumber", orderNumber,
                "amount", amount,
                "status", status,
                "customerId", customerId,
                "createdAt", Instant.now().toString()
        ));
    }

    public List<Map<String, Object>> listOrders() {
        simulatorState.executeSimulation();
        return new ArrayList<>(orders.values());
    }

    public Map<String, Object> getOrderById(String orderId) {
        simulatorState.executeSimulation();
        Map<String, Object> order = orders.get(orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Order not found with id: " + orderId);
        }
        return order;
    }

    public Map<String, Object> createOrder(Map<String, Object> payload) {
        simulatorState.executeSimulation();
        String id = "ord-" + UUID.randomUUID().toString().substring(0, 8);
        String orderNumber = "ORD-" + System.currentTimeMillis() % 100000;
        double amount = payload.containsKey("amount") ? Double.parseDouble(payload.get("amount").toString()) : 100.00;
        String customerId = payload.containsKey("customerId") ? payload.get("customerId").toString() : "cust-default";

        Map<String, Object> newOrder = Map.of(
                "id", id,
                "orderNumber", orderNumber,
                "amount", amount,
                "status", "CREATED",
                "customerId", customerId,
                "createdAt", Instant.now().toString()
        );
        orders.put(id, newOrder);
        return newOrder;
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
