package ru.spb.aiagent.application.core;

/**
 * Таймаут, полученный от AI-клиента через application port contract.
 */
public class AiTimeoutException extends RuntimeException {
    /**
     * Создаёт ошибку таймаута AI-клиента.
     */
    public AiTimeoutException(String message) {
        super(message);
    }
}
