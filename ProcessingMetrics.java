package org.acme.sqs.consumer;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

@ApplicationScoped
public class ProcessingMetrics {

    private static final Logger LOGGER = Logger.getLogger(ProcessingMetrics.class.getName());

    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);

    public void reset() {
        totalProcessed.set(0);
        totalFailed.set(0);
    }

    public void recordSuccess() {
        totalProcessed.incrementAndGet();
    }

    public void recordFailure() {
        totalFailed.incrementAndGet();
    }

    public void logCycleSummary(int totalMessages, long elapsedMs) {
        String summary = String.format(
            "CYCLE SUMMARY - Total: %d | Processed: %d | Failed: %d | Time: %dms",
            totalMessages,
            totalProcessed.get(),
            totalFailed.get(),
            elapsedMs
        );
        
        if (totalFailed.get() > 0) {
            LOGGER.warning(summary);
        } else {
            LOGGER.info(summary);
        }
    }

    public int getProcessedCount() { return totalProcessed.get(); }
    public int getFailedCount() { return totalFailed.get(); }
}