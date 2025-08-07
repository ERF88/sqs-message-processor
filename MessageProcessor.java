package org.acme.sqs.consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

@ApplicationScoped
public class MessageProcessor {

    private static final Logger LOGGER = Logger.getLogger(MessageProcessor.class.getName());
    private static final int TARGET_MESSAGES_PER_CYCLE = 100;

    @Inject
    ExternalService externalService;

    @Inject
    MessageLifecycleManager lifecycleManager;

    private final Semaphore rateLimiter = new Semaphore(TARGET_MESSAGES_PER_CYCLE);

    public ProcessingResult processMessage(Message message) {
        try {
            rateLimiter.acquire();
            return processSingleMessage(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProcessingResult.failed("Processing interrupted");
        } finally {
            rateLimiter.release();
        }
    }

    private ProcessingResult processSingleMessage(Message message) {
        String messageId = message.messageId();
        try {
            LOGGER.fine("Processing message: " + messageId);
            
            externalService.call(message.body());
            lifecycleManager.deleteMessage(message);
            
            LOGGER.fine("Successfully processed message: " + messageId);
            return ProcessingResult.success();
            
        } catch (Exception e) {
            LOGGER.warning("Failed to process message " + messageId + ": " + e.getMessage());
            lifecycleManager.handleFailure(message, e);
            return ProcessingResult.failed(e.getMessage());
        }
    }

    public static class ProcessingResult {
        private final boolean success;
        private final String error;

        private ProcessingResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }

        public static ProcessingResult success() {
            return new ProcessingResult(true, null);
        }

        public static ProcessingResult failed(String error) {
            return new ProcessingResult(false, error);
        }

        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
}