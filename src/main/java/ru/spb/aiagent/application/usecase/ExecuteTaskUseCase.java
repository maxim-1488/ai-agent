package ru.spb.aiagent.application.usecase;

import ru.spb.aiagent.application.core.AiClient;
import ru.spb.aiagent.application.core.TaskEventPublisher;
import ru.spb.aiagent.application.core.TaskExecutionRegistry;
import ru.spb.aiagent.application.core.TaskRepository;
import ru.spb.aiagent.domain.model.Task;
import ru.spb.aiagent.domain.model.TaskStatus;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use case выполнения AI-задачи: старт, progress, terminal update, публикация событий.
 */
public class ExecuteTaskUseCase {
    private static final Logger log = LoggerFactory.getLogger(ExecuteTaskUseCase.class);

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
        log.debug("Scheduling AI task execution: taskId={}, clientId={}, status={}, version={}",
                task.id(), task.clientId(), task.status(), task.version());
        if (!registry.register(task.id())) {
            log.warn("AI task execution registration skipped because task is already registered: taskId={}", task.id());
            return;
        }
        repository.markInProgress(task.id(), task.version())
                .onSuccess(started -> log.info("AI task started: taskId={}, clientId={}, status={}",
                        started.id(), started.clientId(), started.status()))
                .compose(started -> publisher.publish("TASK_STARTED", started).map(started))
                .compose(started -> aiClient.run(task.id().toString(), task.prompt(), progress ->
                                repository.updateProgress(task.id(), progress)
                                        .onSuccess(updated -> log.debug("AI task progress updated: taskId={}, progress={}, status={}, version={}",
                                                updated.id(), updated.progress(), updated.status(), updated.version()))
                                        .compose(updated -> publisher.publish("TASK_PROGRESS", updated)),
                        () -> registry.isCancelled(task.id())))
                .compose(result -> repository.complete(task.id(), TaskStatus.COMPLETED, result, null))
                .onSuccess(done -> log.info("AI task completed: taskId={}, clientId={}, status={}",
                        done.id(), done.clientId(), done.status()))
                .compose(done -> publisher.publish("TASK_COMPLETED", done).map(done))
                .recover(error -> {
                    if (registry.isCancelled(task.id())) {
                        log.info("AI task execution stopped after cancellation: taskId={}", task.id());
                        return Future.succeededFuture();
                    }
                    TaskStatus status = error.getClass().getSimpleName().contains("Timeout") ? TaskStatus.TIMED_OUT : TaskStatus.FAILED;
                    return repository.complete(task.id(), status, null, error.getMessage())
                            .onSuccess(done -> {
                                if (status == TaskStatus.TIMED_OUT) {
                                    log.warn("AI task timed out: taskId={}, clientId={}, status={}, reason={}",
                                            done.id(), done.clientId(), done.status(), safeReason(error));
                                } else {
                                    log.error("AI task failed: taskId={}, clientId={}, status={}, reason={}",
                                            done.id(), done.clientId(), done.status(), safeReason(error), error);
                                }
                            })
                            .compose(done -> publisher.publish(status == TaskStatus.TIMED_OUT ? "TASK_FAILED" : "TASK_FAILED", done).map(done))
                            .mapEmpty();
                })
                .onComplete(done -> {
                    if (done.failed()) {
                        log.error("Unexpected error in AI task asynchronous chain: taskId={}", task.id(), done.cause());
                    }
                    registry.unregister(task.id());
                    log.debug("AI task execution unregistered: taskId={}", task.id());
                });
    }

    private String safeReason(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
