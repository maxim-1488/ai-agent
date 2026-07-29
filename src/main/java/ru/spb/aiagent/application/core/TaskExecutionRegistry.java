package ru.spb.aiagent.application.core;

import java.util.UUID;

/**
 * Реестр активных выполнений, предотвращающий повторный старт и поддерживающий отмену.
 */
public interface TaskExecutionRegistry {
    /**
     * Регистрирует выполнение, если оно ещё не активно.
     */
    boolean register(UUID taskId);

    /**
     * Запрашивает отмену активного выполнения.
     */
    void cancel(UUID taskId);

    /**
     * Проверяет, отменена ли задача.
     */
    boolean isCancelled(UUID taskId);

    /**
     * Удаляет выполнение из реестра после завершения.
     */
    void unregister(UUID taskId);
}
