package ru.spb.aiagent.application.usecase;

import ru.spb.aiagent.application.core.AiClient;
import ru.spb.aiagent.application.core.TaskEventPublisher;
import ru.spb.aiagent.application.core.TaskExecutionRegistry;
import ru.spb.aiagent.application.core.TaskRepository;
import ru.spb.aiagent.domain.model.Task;
import ru.spb.aiagent.domain.model.TaskStatus;
import io.vertx.core.Future;

/**
 * Use case выполнения AI-задачи: старт, progress, terminal update, публикация событий.
 */
public class ExecuteTaskUseCase {
    private final TaskRepository repository;
    private final AiClient aiClient;
    private final TaskExecutionRegistry registry;
    private final TaskEventPublisher publisher;

    /**
     * Создаёт use case.
     */
    public ExecuteTaskUseCase(TaskRepository repository, AiClient aiClient, TaskExecutionRegistry registry, TaskEventPublisher publisher) {
        this.repository = repository;
        this.aiClient = aiClient;
        this.registry = registry;
        this.publisher = publisher;
    }

    /**
     * Запускает выполнение без блокировки HTTP-потока.
     */
    public void executeAsync(Task task) {
        if (!registry.register(task.id())) {
            return;
        }
        repository.markInProgress(task.id(), task.version())
                .compose(started -> publisher.publish("TASK_STARTED", started).map(started))
                .compose(started -> aiClient.run(task.id().toString(), task.prompt(), progress ->
                                repository.updateProgress(task.id(), progress)
                                        .compose(updated -> publisher.publish("TASK_PROGRESS", updated)),
                        () -> registry.isCancelled(task.id())))
                .compose(result -> repository.complete(task.id(), TaskStatus.COMPLETED, result, null))
                .compose(done -> publisher.publish("TASK_COMPLETED", done).map(done))
                .recover(error -> {
                    if (registry.isCancelled(task.id())) {
                        return Future.succeededFuture();
                    }
                    TaskStatus status = error.getClass().getSimpleName().contains("Timeout") ? TaskStatus.TIMED_OUT : TaskStatus.FAILED;
                    return repository.complete(task.id(), status, null, error.getMessage())
                            .compose(done -> publisher.publish(status == TaskStatus.TIMED_OUT ? "TASK_FAILED" : "TASK_FAILED", done).map(done))
                            .mapEmpty();
                })
                .onComplete(ignored -> registry.unregister(task.id()));
    }
}
