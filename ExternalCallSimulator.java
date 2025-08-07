package org.acme.sqs.consumer;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.logging.Logger;

@ApplicationScoped
public class ExternalCallSimulator {

    private static final Logger LOGGER = Logger.getLogger(ExternalCallSimulator.class.getName());
    private static final double FAILURE_RATE = 0.1;
    private static final int MIN_LATENCY = 40;
    private static final int MAX_LATENCY = 60;

    public void simulateCall(String payload) throws ExternalServiceException {
        simulateLatency();
        simulateFailures(payload);
        
        LOGGER.fine("Successfully processed: " + payload);
    }

    private void simulateLatency() {
        try {
            // 90% das chamadas: 40-1000ms (resposta normal)
            // 10% das chamadas: 1-15 segundos (resposta lenta)
            int latency;
            if (Math.random() < 0.9) {
                latency = MIN_LATENCY + (int) (Math.random() * (1000 - MIN_LATENCY));
            } else {
                latency = 1000 + (int) (Math.random() * 14000); // 1-15 segundos
            }
            
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Simulation interrupted", e);
        }
    }

    private void simulateFailures(String payload) throws ExternalServiceException {
        if (Math.random() < FAILURE_RATE) {
            throw new ExternalServiceException("Simulated failure for: " + payload);
        }
    }

    public static class ExternalServiceException extends Exception {
        public ExternalServiceException(String message) {
            super(message);
        }
    }
}