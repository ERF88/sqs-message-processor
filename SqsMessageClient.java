package org.acme.sqs.client;

import io.quarkus.arc.properties.ConfigProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cliente responsável pelas operações com SQS
 */
@ApplicationScoped
public class SqsMessageClient {

    private static final Logger LOGGER = Logger.getLogger(SqsMessageClient.class.getName());
    private static final int MAX_MESSAGES_PER_RECEIVE = 10;
    private static final int MAX_RECEIVE_CALLS = 10;

    @Inject
    SqsClient sqsClient;

    @ConfigProperty(name = "queue.url")
    String queueUrl;

    public List<Message> receiveMessagesInBatches(int maxMessages) {
        List<Message> allMessages = new ArrayList<>(maxMessages);
        int receivedCount = 0;
        
        for (int i = 0; i < MAX_RECEIVE_CALLS && receivedCount < maxMessages; i++) {
            List<Message> batch = receiveMessages(MAX_MESSAGES_PER_RECEIVE);
            allMessages.addAll(batch);
            receivedCount += batch.size();
            
            if (batch.size() < MAX_MESSAGES_PER_RECEIVE) {
                break; // Não há mais mensagens na fila
            }
        }
        return allMessages;
    }

    public List<Message> receiveMessages(int maxMessages) {
        try {
            ReceiveMessageResponse response = sqsClient.receiveMessage(
                ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(maxMessages)
                    .waitTimeSeconds(0)
                    .attributeNamesWithStrings("ApproximateReceiveCount")
                    .build()
            );
            return response.messages();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error receiving messages: " + e.getMessage(), e);
            return List.of();
        }
    }

    public void deleteMessage(Message message) {
        try {
            sqsClient.deleteMessage(
                DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build()
            );
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error deleting message: " + e.getMessage(), e);
        }
    }

    public void rescheduleMessage(Message message, int visibilityTimeout) {
        try {
            sqsClient.changeMessageVisibility(
                ChangeMessageVisibilityRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .visibilityTimeout(visibilityTimeout)
                    .build()
            );
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error rescheduling message: " + e.getMessage(), e);
        }
    }
}