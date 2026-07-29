package ru.spb.aiagent.domain.exception;

/**
 * Исключение конфликтного конкурентного изменения задачи при optimistic locking.
 */
public class TaskConflictException extends TaskDomainException {
    /**
     * Создаёт исключение конфликта.
     */
    public TaskConflictException(String message) {
        super(message);
    }
}
