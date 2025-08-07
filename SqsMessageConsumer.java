package org.acme.sqs.consumer;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.sqs.model.Message;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class SqsMessageConsumer {

    private static final Logger LOGGER = Logger.getLogger(SqsMessageConsumer.class.getName());

    @Inject
    MessageBatchReceiver messageReceiver;

    @Inject
    MessageProcessor messageProcessor;

    @Inject
    ProcessingMetrics metrics;

    private final AtomicBoolean processingActive = new AtomicBoolean(false);
    private volatile long cycleStartTime;

    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduleBatchProcessing() {
        if (!processingActive.compareAndSet(false, true)) {
            long runningTime = System.currentTimeMillis() - cycleStartTime;
            LOGGER.info("Previous batch still processing for " + runningTime + "ms. Skipping this cycle.");
            return;
        }

        try {
            cycleStartTime = System.currentTimeMillis();
            processBatch();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            processingActive.set(false);
        }
    }

    private void processBatch() throws InterruptedException {
        Instant start = Instant.now();
        metrics.reset();
        
        List<Message> messages = messageReceiver.receiveMessages();
        if (messages.isEmpty()) {
            LOGGER.info("No messages to process");
            return;
        }

        LOGGER.info("Starting batch processing of " + messages.size() + " messages");
        processMessagesInParallel(messages);
        
        long elapsed = Duration.between(start, Instant.now()).toMillis();
        metrics.logCycleSummary(messages.size(), elapsed);
        
        LOGGER.info("Batch completed in " + elapsed + "ms");
    }

    private void processMessagesInParallel(List<Message> messages) throws InterruptedException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>(messages.size());
            
            for (Message message : messages) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    var result = messageProcessor.processMessage(message);
                    if (result.isSuccess()) {
                        metrics.recordSuccess();
                    } else {
                        metrics.recordFailure();
                    }
                }, executor);
                futures.add(future);
            }
            
            // Aguarda TODAS as tasks completarem, sem timeout
            CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
            
            try {
                LOGGER.info("Waiting for all " + messages.size() + " messages to complete...");
                allTasks.get(); // Sem timeout - aguarda até completar
                LOGGER.info("All messages processed successfully");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in parallel processing: " + e.getMessage(), e);
                
                // Em caso de erro, ainda aguarda as tasks completarem
                for (CompletableFuture<Void> future : futures) {
                    try {
                        future.get(100, TimeUnit.MILLISECONDS);
                    } catch (Exception ignored) {
                        // Task individual falhou ou ainda está executando
                    }
                }
            }
        }
    }
}