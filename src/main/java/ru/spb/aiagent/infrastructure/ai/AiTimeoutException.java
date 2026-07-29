package ru.spb.aiagent.infrastructure.ai;

/**
 * Ошибка таймаута mock AI-выполнения, маппится в TIMED_OUT.
 */
public class AiTimeoutException extends RuntimeException {
    /**
     * Создаёт ошибку таймаута.
     */
    public AiTimeoutException(String message) {
        super(message);
    }
}
