package ru.spb.aiagent.application.usecase;

import ru.spb.aiagent.application.core.TaskEventPublisher;
import ru.spb.aiagent.application.core.TaskExecutionRegistry;
import ru.spb.aiagent.application.core.TaskRepository;
import ru.spb.aiagent.domain.model.Task;
import io.vertx.core.Future;
import java.util.UUID;

/**
 * Use case отмены задачи, конкурирующий с завершением через optimistic locking в репозитории.
 */
public class CancelTaskUseCase {
    private final TaskRepository repository;
    private final TaskExecutionRegistry registry;
    private final TaskEventPublisher publisher;

    /**
     * Создаёт use case.
     */
    public CancelTaskUseCase(TaskRepository repository, TaskExecutionRegistry registry, TaskEventPublisher publisher) {
        this.repository = repository;
        this.registry = registry;
        this.publisher = publisher;
    }

    /**
     * Отменяет CREATED/IN_PROGRESS задачу клиента и сигналит активному выполнению.
     */
    public Future<Task> cancel(String clientId, UUID taskId) {
        registry.cancel(taskId);
        return repository.cancel(taskId, clientId)
                .compose(task -> publisher.publish("TASK_CANCELLED", task).map(task));
    }
}
