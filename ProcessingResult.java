package org.acme.sqs.model;

/**
 * Representa o resultado do processamento de uma mensagem
 */
public class ProcessingResult {
    
    public enum Status {
        SUCCESS,
        FAILURE_RESCHEDULED,
        FAILURE_MAX_RETRIES_REACHED,
        FAILURE_CRITICAL
    }

    private final String messageId;
    private final Status status;
    private final Exception exception;
    private final int attemptCount;
    private final int visibilityTimeout;

    private ProcessingResult(String messageId, Status status, Exception exception, 
                           int attemptCount, int visibilityTimeout) {
        this.messageId = messageId;
        this.status = status;
        this.exception = exception;
        this.attemptCount = attemptCount;
        this.visibilityTimeout = visibilityTimeout;
    }

    public static ProcessingResult success(String messageId) {
        return new ProcessingResult(messageId, Status.SUCCESS, null, 0, 0);
    }

    public static ProcessingResult failureRescheduled(String messageId, int attemptCount, 
                                                    int visibilityTimeout, Exception exception) {
        return new ProcessingResult(messageId, Status.FAILURE_RESCHEDULED, exception, 
                                  attemptCount, visibilityTimeout);
    }

    public static ProcessingResult failureMaxRetriesReached(String messageId, Exception exception) {
        return new ProcessingResult(messageId, Status.FAILURE_MAX_RETRIES_REACHED, exception, 0, 0);
    }

    public static ProcessingResult failureCritical(String messageId, Exception exception) {
        return new ProcessingResult(messageId, Status.FAILURE_CRITICAL, exception, 0, 0);
    }

    // Getters
    public String getMessageId() {
        return messageId;
    }

    public Status getStatus() {
        return status;
    }

    public Exception getException() {
        return exception;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getVisibilityTimeout() {
        return visibilityTimeout;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isFailure() {
        return status != Status.SUCCESS;
    }

    @Override
    public String toString() {
        return String.format("ProcessingResult{messageId='%s', status=%s, attemptCount=%d}", 
                           messageId, status, attemptCount);
    }
}