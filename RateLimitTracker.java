package org.acme.sqs.consumer;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

@ApplicationScoped
public class RateLimitTracker {

    private static final Logger LOGGER = Logger.getLogger(RateLimitTracker.class.getName());
    private static final int WARNING_THRESHOLD = 95;

    private final AtomicInteger requestCount = new AtomicInteger(0);
    private volatile long lastResetTime = System.currentTimeMillis();

    public synchronized void trackRequest() {
        resetCounterIfNeeded();
        
        int count = requestCount.incrementAndGet();
        if (count > WARNING_THRESHOLD) {
            LOGGER.warning("APPROACHING RATE LIMIT! Current TPS: " + count);
        }
    }

    private void resetCounterIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastResetTime > 1000) {
            requestCount.set(0);
            lastResetTime = now;
        }
    }

    public int getCurrentTps() {
        return requestCount.get();
    }
}