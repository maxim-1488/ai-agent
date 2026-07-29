package ru.spb.aiagent.application.core;

import ru.spb.aiagent.domain.model.Task;
import ru.spb.aiagent.domain.model.TaskFilter;
import ru.spb.aiagent.domain.model.TaskPage;
import ru.spb.aiagent.domain.model.TaskStatus;
import io.vertx.core.Future;
import java.util.UUID;

/**
 * Порт хранения задач с optimistic locking и клиентской изоляцией.
 */
public interface TaskRepository {
    /**
     * Создаёт задачу и событие TASK_CREATED в одной транзакции.
     */
    Future<Task> create(Task task);

    /**
     * Ищет задачу только для указанного клиента.
     */
    Future<Task> findByIdAndClientId(UUID id, String clientId);

    /**
     * Возвращает страницу задач клиента.
     */
    Future<TaskPage> list(String clientId, TaskFilter filter);

    /**
     * Переводит задачу в IN_PROGRESS при совпадении version.
     */
    Future<Task> markInProgress(UUID id, long version);

    /**
     * Обновляет progress без отката назад и только для нетерминальной задачи.
     */
    Future<Task> updateProgress(UUID id, int progress);

    /**
     * Завершает задачу терминальным статусом и пишет событие в транзакции.
     */
    Future<Task> complete(UUID id, TaskStatus status, String result, String errorMessage);

    /**
     * Отменяет задачу клиента атомарно при статусе CREATED или IN_PROGRESS.
     */
    Future<Task> cancel(UUID id, String clientId);
}
