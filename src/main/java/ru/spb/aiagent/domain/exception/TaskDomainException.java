package ru.spb.aiagent.domain.exception;

/**
 * Базовое доменное исключение AI Agent без утечки инфраструктурных деталей наружу.
 */
public class TaskDomainException extends RuntimeException {
    /**
     * Создаёт доменное исключение.
     *
     * @param message сообщение для безопасного API-маппинга
     */
    public TaskDomainException(String message) {
        super(message);
    }
}
