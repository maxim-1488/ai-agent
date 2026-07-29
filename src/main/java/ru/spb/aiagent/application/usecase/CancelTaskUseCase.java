package ru.spb.aiagent.application.usecase;

import ru.spb.aiagent.application.core.TaskEventPublisher;
import ru.spb.aiagent.application.core.TaskExecutionRegistry;
import ru.spb.aiagent.application.core.TaskEventType;
import ru.spb.aiagent.application.core.TaskRepository;
import ru.spb.aiagent.domain.model.Task;
import io.vertx.core.Future;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use case отмены задачи, конкурирующий с завершением через optimistic locking в репозитории.
 */
public class CancelTaskUseCase {
    private static final Logger log = LoggerFactory.getLogger(CancelTaskUseCase.class);

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
        log.debug("Starting cancel AI task use case: taskId={}, clientId={}", taskId, clientId);
        return repository.cancel(taskId, clientId)
                .onSuccess(task -> log.info("AI task cancelled: taskId={}, clientId={}, status={}",
                        task.id(), task.clientId(), task.status()))
                .onFailure(error -> log.warn("AI task cancellation failed or conflicted: taskId={}, clientId={}, reason={}",
                        taskId, clientId, error.getMessage()))
                .compose(task -> {
                    registry.cancel(taskId);
                    return publisher.publish(TaskEventType.CANCELLED, task).map(task);
                });
    }
}
