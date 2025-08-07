package org.acme.sqs.consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class MessageLifecycleManager {

    private static final Logger LOGGER = Logger.getLogger(MessageLifecycleManager.class.getName());

    @Inject
    SqsClient sqsClient;

    @Inject
    QueueConfiguration queueConfig;

    public void deleteMessage(Message message) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueConfig.getQueueUrl())
                .receiptHandle(message.receiptHandle())
                .build());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error deleting message: " + e.getMessage(), e);
        }
    }

    public void handleFailure(Message message, Exception ex) {
        try {
            String messageId = message.messageId();
            int receiveCount = getReceiveCount(message);

            LOGGER.log(Level.WARNING, String.format(
                "Message processing failed (attempt %d): %s | Error: %s | SQS will handle retry automatically",
                receiveCount, messageId, ex.getMessage()
            ), ex);
            
            // Não fazemos nada - deixamos a mensagem retornar ao visibility timeout
            // O SQS irá automaticamente reprocessar ou mover para DLQ conforme configurado
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling failed message: " + e.getMessage(), e);
        }
    }

    private int getReceiveCount(Message message) {
        String receiveCountStr = message.attributes().get("ApproximateReceiveCount");
        return receiveCountStr != null ? Integer.parseInt(receiveCountStr) : 1;
    }
}