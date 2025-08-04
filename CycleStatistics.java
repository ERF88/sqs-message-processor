package org.acme.sqs.statistics;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * Coletor e reporter de estatísticas de processamento
 */
@ApplicationScoped
public class CycleStatistics {

    private static final Logger LOGGER = Logger.getLogger(CycleStatistics.class.getName());

    private Instant cycleStartTime;
    private int totalMessages;
    private int processedMessages;
    private int failedMessages;

    public void startCycle() {
        cycleStartTime = Instant.now();
        totalMessages = 0;
        processedMessages = 0;
        failedMessages = 0;
    }

    public void setTotalMessages(int total) {
        this.totalMessages = total;
    }

    public void incrementProcessed() {
        processedMessages++;
    }

    public void incrementFailed() {
        failedMessages++;
    }

    public void setCounters(int processed, int failed) {
        this.processedMessages = processed;
        this.failedMessages = failed;
    }

    public long getElapsedTimeMs() {
        if (cycleStartTime == null) {
            return 0;
        }
        return Duration.between(cycleStartTime, Instant.now()).toMillis();
    }

    public void logCycleSummary() {
        long elapsedMs = getElapsedTimeMs();
        
        String summary = String.format(
            "CYCLE SUMMARY - Total: %d | Processed: %d | Failed: %d | Time: %dms | TPS: %.2f",
            totalMessages,
            processedMessages,
            failedMessages,
            elapsedMs,
            calculateThroughput(elapsedMs)
        );
        
        if (failedMessages > 0) {
            LOGGER.warning(summary);
        } else {
            LOGGER.info(summary);
        }
    }

    private double calculateThroughput(long elapsedMs) {
        if (elapsedMs == 0) {
            return 0.0;
        }
        return (double) processedMessages / (elapsedMs / 1000.0);
    }

    // Getters
    public int getTotalMessages() {
        return totalMessages;
    }

    public int getProcessedMessages() {
        return processedMessages;
    }

    public int getFailedMessages() {
        return failedMessages;
    }

    public double getSuccessRate() {
        if (totalMessages == 0) {
            return 0.0;
        }
        return (double) processedMessages / totalMessages * 100;
    }
}