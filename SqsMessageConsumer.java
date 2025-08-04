package org.acme.sqs.consumer;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.sqs.client.SqsMessageClient;
import org.acme.sqs.limiter.RateLimiter;
import org.acme.sqs.processor.SqsMessageProcessor;
import org.acme.sqs.statistics.CycleStatistics;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Coordenador principal do processamento de mensagens SQS
 * Responsável pelo agendamento e orquestração dos componentes
 */
@ApplicationScoped
public class SqsMessageConsumer {

    private static final Logger LOGGER = Logger.getLogger(SqsMessageConsumer.class.getName());
    private static final int MESSAGES_PER_CYCLE = 100;
    private static final long CYCLE_TIMEOUT_MS = 950;

    @Inject
    SqsMessageClient sqsMessageClient;

    @Inject
    SqsMessageProcessor messageProcessor;

    @Inject
    RateLimiter rateLimiter;

    @Inject
    CycleStatistics statistics;

    private final AtomicBoolean processingActive = new AtomicBoolean(false);

    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void scheduleBatchProcessing() {
        if (!processingActive.compareAndSet(false, true)) {
            LOGGER.warning("Previous batch still processing. Skipping this cycle.");
            return;
        }

        try {
            processBatch();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Batch processing interrupted");
        } catch (Exception e) {
            LOGGER.severe("Unexpected error during batch processing: " + e.getMessage());
        } finally {
            processingActive.set(false);
        }
    }

    private void processBatch() throws InterruptedException {
        statistics.startCycle();
        messageProcessor.resetCounters();
        
        // Recebe mensagens em lotes
        List<Message> messages = sqsMessageClient.receiveMessagesInBatches(MESSAGES_PER_CYCLE);
        statistics.setTotalMessages(messages.size());
        
        if (messages.isEmpty()) {
            LOGGER.fine("No messages to process in this cycle");
            return;
        }

        LOGGER.info("Received " + messages.size() + " messages for processing");
        
        // Processa mensagens em paralelo com virtual threads
        processMessagesInParallel(messages);
        
        // Atualiza estatísticas e faz log do resumo
        updateStatisticsAndLog();
        
        // Aguarda até completar 1 segundo do ciclo
        waitForCycleCompletion();
    }

    private void processMessagesInParallel(List<Message> messages) throws InterruptedException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Message message : messages) {
                executor.submit(() -> 
                    rateLimiter.executeWithRateLimit(() -> 
                        messageProcessor.processMessage(message)
                    )
                );
            }
            
            executor.shutdown();
            if (!executor.awaitTermination(CYCLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                LOGGER.warning("Batch processing timed out before completing all tasks");
            }
        }
    }

    private void updateStatisticsAndLog() {
        statistics.setCounters(
            messageProcessor.getTotalProcessed(), 
            messageProcessor.getTotalFailed()
        );
        statistics.logCycleSummary();
    }

    private void waitForCycleCompletion() throws InterruptedException {
        long elapsed = statistics.getElapsedTimeMs();
        long remaining = 1000 - elapsed;
        
        if (remaining > 0) {
            LOGGER.fine("Waiting " + remaining + "ms to complete cycle");
            Thread.sleep(remaining);
        }
    }

    // Métodos para monitoramento/debugging
    public boolean isProcessingActive() {
        return processingActive.get();
    }

    public int getAvailableRateLimit() {
        return rateLimiter.getAvailablePermits();
    }
}