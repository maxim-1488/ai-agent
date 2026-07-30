package ru.spb.aiagent.application.core;

/**
 * Timeout reported by an AI client adapter through the application port contract.
 */
public class AiTimeoutException extends RuntimeException {
    /**
     * Creates an AI timeout error.
     */
    public AiTimeoutException(String message) {
        super(message);
    }
}
