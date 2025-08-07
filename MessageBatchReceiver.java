package org.acme.sqs.consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class MessageBatchReceiver {
    
    private static final Logger LOGGER = Logger.getLogger(MessageBatchReceiver.class.getName());
    private static final int MAX_MESSAGES_PER_RECEIVE = 10;
    private static final int TARGET_MESSAGES_PER_CYCLE = 100;
    private static final int MAX_RECEIVE_BATCHES = 10;

    @Inject
    SqsClient sqsClient;

    @Inject
    QueueConfiguration queueConfig;

    public List<Message> receiveMessages() {
        List<Message> allMessages = new ArrayList<>(TARGET_MESSAGES_PER_CYCLE);
        int batchCount = 0;
        
        // Recebimento sequencial para manter simplicidade e evitar rate limiting do SQS
        while (allMessages.size() < TARGET_MESSAGES_PER_CYCLE && batchCount < MAX_RECEIVE_BATCHES) {
            List<Message> batch = receiveSingleBatch(MAX_MESSAGES_PER_RECEIVE);
            
            // Se circuit breaker retornou lista vazia por falha, para o loop
            if (batch.isEmpty() && batchCount == 0) {
                LOGGER.warning("SQS circuit breaker may be open - no messages received");
                break;
            }
            
            allMessages.addAll(batch);
            batchCount++;
            
            // Se recebeu menos que o máximo, não há mais mensagens
            if (batch.size() < MAX_MESSAGES_PER_RECEIVE) {
                break;
            }
        }
        
        LOGGER.info("Received " + allMessages.size() + " messages in " + batchCount + " batches");
        return allMessages.size() > TARGET_MESSAGES_PER_CYCLE ? 
               allMessages.subList(0, TARGET_MESSAGES_PER_CYCLE) : allMessages;
    }

    @CircuitBreaker(
        requestVolumeThreshold = 5,      // Menor threshold - SQS deve ser mais confiável
        failureRatio = 0.6,              // 60% de falhas para abrir
        delay = 30000,                   // 30s esperando SQS se recuperar
        successThreshold = 3             // 3 sucessos para fechar
    )
    @Fallback(fallbackMethod = "fallbackReceiveBatch")
    private List<Message> receiveSingleBatch(int maxMessages) {
        try {
            int actualMax = Math.min(maxMessages, MAX_MESSAGES_PER_RECEIVE);
            
            List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueConfig.getQueueUrl())
                    .maxNumberOfMessages(actualMax)
                    .waitTimeSeconds(0)
                    .attributeNamesWithStrings("ApproximateReceiveCount")
                    .build()).messages();
                    
            // Log success apenas se havia falhas antes
            if (!messages.isEmpty()) {
                LOGGER.fine("Successfully received " + messages.size() + " messages from SQS");
            }
            
            return messages;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error receiving messages from SQS: " + e.getMessage(), e);
            throw e; // Re-throw para ativar circuit breaker
        }
    }

    // Fallback method - retorna lista vazia quando SQS está indisponível
    private List<Message> fallbackReceiveBatch(int maxMessages) {
        LOGGER.warning("SQS Circuit Breaker is OPEN - falling back to empty batch. " +
                      "Will retry in 30 seconds.");
        return List.of();
    }
}