package com.edvexa.gateway.simulator;

import com.edvexa.gateway.simulator.dto.SimulatorConfigDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SimulatorState {

    private final String serviceName;
    private volatile int delayMs = 0;
    private volatile double failureRate = 0.0;
    private volatile int failureStatusCode = 500;
    private final AtomicInteger forcedFailureCount = new AtomicInteger(0);

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);

    public SimulatorState(String serviceName) {
        this.serviceName = serviceName;
    }

    public void executeSimulation() {
        totalRequests.incrementAndGet();

        // 1. Configured artificial delay
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Simulator thread interrupted during delay");
            }
        }

        // 2. Deterministic forced failures (e.g., fail next N calls)
        int remainingForced = forcedFailureCount.get();
        while (remainingForced > 0) {
            if (forcedFailureCount.compareAndSet(remainingForced, remainingForced - 1)) {
                totalFailures.incrementAndGet();
                throw new ResponseStatusException(HttpStatus.valueOf(failureStatusCode),
                        "Deterministic simulated failure in " + serviceName + " (remaining forced: " + (remainingForced - 1) + ")");
            }
            remainingForced = forcedFailureCount.get();
        }

        // 3. Probabilistic failure rate
        if (failureRate > 0.0) {
            double randomVal = ThreadLocalRandom.current().nextDouble();
            if (randomVal < failureRate) {
                totalFailures.incrementAndGet();
                throw new ResponseStatusException(HttpStatus.valueOf(failureStatusCode),
                        "Probabilistic simulated failure in " + serviceName + " (rate: " + failureRate + ")");
            }
        }
    }

    public void updateConfig(SimulatorConfigDto config) {
        if (config.delayMs() != null) {
            this.delayMs = config.delayMs();
        }
        if (config.failureRate() != null) {
            this.failureRate = config.failureRate();
        }
        if (config.failureStatusCode() != null) {
            this.failureStatusCode = config.failureStatusCode();
        }
        if (config.forcedFailureCount() != null) {
            this.forcedFailureCount.set(config.forcedFailureCount());
        }
    }

    public void reset() {
        this.delayMs = 0;
        this.failureRate = 0.0;
        this.failureStatusCode = 500;
        this.forcedFailureCount.set(0);
        this.totalRequests.set(0);
        this.totalFailures.set(0);
    }

    public Map<String, Object> getStatus() {
        return Map.of(
                "serviceName", serviceName,
                "delayMs", delayMs,
                "failureRate", failureRate,
                "failureStatusCode", failureStatusCode,
                "forcedFailureCount", forcedFailureCount.get(),
                "totalRequests", totalRequests.get(),
                "totalFailures", totalFailures.get()
        );
    }

    public int getDelayMs() {
        return delayMs;
    }

    public double getFailureRate() {
        return failureRate;
    }

    public int getFailureStatusCode() {
        return failureStatusCode;
    }

    public int getForcedFailureCount() {
        return forcedFailureCount.get();
    }
}
