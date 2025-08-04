package org.acme.sqs.processor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.sqs.ExternalService;
import org.acme.sqs.client.SqsMessageClient;
import org.acme.sqs.model.ProcessingResult;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Responsável pelo processamento individual de mensagens SQS
 */
@ApplicationScoped
public class SqsMessageProcessor {

    private static final Logger LOGGER = Logger.getLogger(SqsMessageProcessor.class.getName());
    private static final int MAX_RETRIES = 3;
    private static final int BASE_VISIBILITY_TIMEOUT = 10;

    @Inject
    ExternalService externalService;

    @Inject
    SqsMessageClient sqsMessageClient;

    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);

    public ProcessingResult processMessage(Message message) {
        String messageId = message.messageId();
        try {
            LOGGER.fine("Processing message: " + messageId);
            externalService.call(message.body());
            
            sqsMessageClient.deleteMessage(message);
            totalProcessed.incrementAndGet();
            LOGGER.fine("Successfully processed message: " + messageId);
            
            return ProcessingResult.success(messageId);
            
        } catch (Exception e) {
            totalFailed.incrementAndGet();
            return handleProcessingFailure(message, e);
        }
    }

    private ProcessingResult handleProcessingFailure(Message message, Exception ex) {
        try {
            String messageId = message.messageId();
            String receiveCountStr = message.attributes().get("ApproximateReceiveCount");
            int receiveCount = receiveCountStr != null ? Integer.parseInt(receiveCountStr) : 1;

            if (receiveCount > MAX_RETRIES) {
                LOGGER.log(Level.WARNING, String.format(
                    "Message reached max retries (%d) and will be moved to DLQ by SQS: %s | Error: %s",
                    receiveCount, messageId, ex.getMessage()
                ), ex);
                
                return ProcessingResult.failureMaxRetriesReached(messageId, ex);
                
            } else {
                int newVisibilityTimeout = (int) (Math.pow(2, receiveCount) * BASE_VISIBILITY_TIMEOUT);
                sqsMessageClient.rescheduleMessage(message, newVisibilityTimeout);
                
                LOGGER.log(Level.INFO, String.format(
                    "Rescheduling message (attempt %d/%d) in %ds: %s | Error: %s",
                    receiveCount, MAX_RETRIES, newVisibilityTimeout, messageId, ex.getMessage()
                ));
                
                return ProcessingResult.failureRescheduled(messageId, receiveCount, newVisibilityTimeout, ex);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling failed message: " + e.getMessage(), e);
            return ProcessingResult.failureCritical(message.messageId(), e);
        }
    }

    public int getTotalProcessed() {
        return totalProcessed.get();
    }

    public int getTotalFailed() {
        return totalFailed.get();
    }

    public void resetCounters() {
        totalProcessed.set(0);
        totalFailed.set(0);
    }
}