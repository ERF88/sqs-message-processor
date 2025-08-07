package org.acme.sqs.consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;

@ApplicationScoped
public class ExternalService {

    @Inject
    RateLimitTracker rateLimitTracker;

    @Inject
    ExternalCallSimulator callSimulator;

    @CircuitBreaker(
        requestVolumeThreshold = 10,
        failureRatio = 0.3,
        delay = 10000
    )
    @Timeout(16000) // 16 segundos para cobrir os 15s + margem
    public void call(String payload) throws Exception {
        rateLimitTracker.trackRequest();
        callSimulator.simulateCall(payload);
    }
}