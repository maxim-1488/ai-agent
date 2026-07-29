package ru.spb.aiagent.domain.exception;

/**
 * Исключение отсутствия задачи в области видимости клиента.
 */
public class TaskNotFoundException extends TaskDomainException {
    /**
     * Создаёт исключение отсутствия задачи.
     */
    public TaskNotFoundException(String message) {
        super(message);
    }
}
