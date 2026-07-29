package ru.spb.aiagent.infrastructure.ai;

/**
 * Ошибка mock AI-выполнения, маппится в FAILED.
 */
public class AiExecutionException extends RuntimeException {
    /**
     * Создаёт ошибку AI-выполнения.
     */
    public AiExecutionException(String message) {
        super(message);
    }
}
