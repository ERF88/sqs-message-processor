package org.acme.sqs.consumer;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class QueueConfiguration {

    @ConfigProperty(name = "queue.url")
    String queueUrl;

    public String getQueueUrl() {
        return queueUrl;
    }
}