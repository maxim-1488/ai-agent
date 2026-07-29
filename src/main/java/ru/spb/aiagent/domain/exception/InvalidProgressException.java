package ru.spb.aiagent.domain.exception;

/**
 * Исключение нарушения диапазона прогресса 0..100.
 */
public class InvalidProgressException extends TaskDomainException {
    /**
     * Создаёт исключение прогресса.
     */
    public InvalidProgressException(String message) {
        super(message);
    }
}
