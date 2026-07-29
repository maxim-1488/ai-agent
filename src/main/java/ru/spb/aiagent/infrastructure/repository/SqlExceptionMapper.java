package ru.spb.aiagent.infrastructure.repository;

/**
 * Mapper SQL-ошибок в безопасные инфраструктурные исключения без раскрытия текста запроса.
 */
public class SqlExceptionMapper {
    /**
     * Возвращает безопасное исключение для application/web слоёв.
     */
    public RuntimeException map(Throwable error) {
        return new IllegalStateException("Task storage error");
    }
}
