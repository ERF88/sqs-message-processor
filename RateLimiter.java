package org.acme.sqs.limiter;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

/**
 * Controlador de taxa de processamento de mensagens
 */
@ApplicationScoped
public class RateLimiter {

    private static final Logger LOGGER = Logger.getLogger(RateLimiter.class.getName());
    private static final int MESSAGES_PER_CYCLE = 100;

    private final Semaphore semaphore = new Semaphore(MESSAGES_PER_CYCLE);

    /**
     * Executa uma tarefa respeitando o limite de taxa
     */
    public void executeWithRateLimit(Runnable task) {
        try {
            semaphore.acquire();
            task.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Rate limiter interrupted");
        } finally {
            semaphore.release();
        }
    }

    /**
     * Retorna o número de permits disponíveis
     */
    public int getAvailablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * Retorna o número de permits em uso
     */
    public int getPermitsInUse() {
        return MESSAGES_PER_CYCLE - semaphore.availablePermits();
    }

    /**
     * Reset do semáforo (usado entre ciclos)
     */
    public void reset() {
        // Drena todos os permits e depois restaura
        semaphore.drainPermits();
        semaphore.release(MESSAGES_PER_CYCLE);
    }
}