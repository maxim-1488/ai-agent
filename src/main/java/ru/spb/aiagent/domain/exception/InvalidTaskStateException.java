package ru.spb.aiagent.domain.exception;

/**
 * Исключение недопустимого перехода или изменения терминальной задачи.
 */
public class InvalidTaskStateException extends TaskDomainException {
    /**
     * Создаёт исключение состояния задачи.
     */
    public InvalidTaskStateException(String message) {
        super(message);
    }
}
