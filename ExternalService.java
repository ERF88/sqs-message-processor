import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
@ApplicationScoped
public class ExternalService {
    private static final Logger LOGGER = Logger.getLogger(ExternalService.class.getName());
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private volatile long lastResetTime = System.currentTimeMillis();
    @CircuitBreaker(
        requestVolumeThreshold = 10,
        failureRatio = 0.3,
        delay = 10000
    )
    @Timeout(500)
    public void call(String payload) throws Exception {
        trackRequestRate();
        simulateExternalCall(payload);
    }
    private void simulateExternalCall(String payload) throws Exception {
        // Simulação de latência entre 40-60ms
        int latency = 40 + (int) (Math.random() * 20);
        Thread.sleep(latency);

        // Simulação de 10% de falhas
        if (Math.random() < 0.1) {
            throw new ExternalServiceException("Simulated failure for: " + payload);
        }

        LOGGER.fine("Successfully processed: " + payload);
    }
    private synchronized void trackRequestRate() {
        long now = System.currentTimeMillis();
        if (now - lastResetTime > 1000) {
            requestCount.set(0);
            lastResetTime = now;
        }
        int count = requestCount.incrementAndGet();
        if (count > 95) {
            LOGGER.warning("APPROACHING RATE LIMIT! Current TPS: " + count);
        }
    }

    public static class ExternalServiceException extends Exception {
        public ExternalServiceException(String message) {
            super(message);
        }
    }
}
