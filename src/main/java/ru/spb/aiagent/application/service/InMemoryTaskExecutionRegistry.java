package ru.spb.aiagent.application.service;

import ru.spb.aiagent.application.core.TaskExecutionRegistry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory реестр активных выполнений. Не является распределённым между backend instances.
 */
public class InMemoryTaskExecutionRegistry implements TaskExecutionRegistry {
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final Set<UUID> cancelled = ConcurrentHashMap.newKeySet();

    /**
     * Регистрирует задачу только один раз.
     */
    @Override
    public boolean register(UUID taskId) {
        cancelled.remove(taskId);
        return active.add(taskId);
    }

    /**
     * Помечает задачу как отменённую.
     */
    @Override
    public void cancel(UUID taskId) {
        cancelled.add(taskId);
    }

    /**
     * Проверяет флаг отмены.
     */
    @Override
    public boolean isCancelled(UUID taskId) {
        return cancelled.contains(taskId);
    }

    /**
     * Удаляет задачу из активных выполнений.
     */
    @Override
    public void unregister(UUID taskId) {
        active.remove(taskId);
        cancelled.remove(taskId);
    }
}
