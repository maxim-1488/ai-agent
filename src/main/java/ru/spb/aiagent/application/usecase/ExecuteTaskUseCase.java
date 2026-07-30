package ru.spb.aiagent.application.usecase;

import ru.spb.aiagent.application.core.AiClient;
import ru.spb.aiagent.application.core.AiTimeoutException;
import ru.spb.aiagent.application.core.TaskEventPublisher;
import ru.spb.aiagent.application.core.TaskEventType;
import ru.spb.aiagent.application.core.TaskExecutionRegistry;
import ru.spb.aiagent.application.core.TaskRepository;
import ru.spb.aiagent.domain.exception.TaskConflictException;
import ru.spb.aiagent.domain.model.Task;
import ru.spb.aiagent.domain.model.TaskStatus;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use case выполнения AI-задачи.
 *
 * <p>Сценарий отвечает за запуск уже созданной задачи, перевод в {@code IN_PROGRESS},
 * вызов AI-порта, обработку progress, завершение успешным или ошибочным terminal status
 * и очистку реестра активных выполнений. Отмена состояния задачи выполняется отдельным
 * {@link CancelTaskUseCase}; этот use case только прекращает обработку отменённого выполнения.
 */
public class ExecuteTaskUseCase {
    private static final Logger log = LoggerFactory.getLogger(ExecuteTaskUseCase.class);

    private final TaskRepository repository;
    private final AiClient aiClient;
    private final TaskExecutionRegistry registry;
    private final TaskEventPublisher publisher;

    /**
     * Создаёт use case выполнения AI-задачи.
     *
     * @param repository порт хранения задач
     * @param aiClient порт AI-клиента
     * @param registry реестр активных выполнений
     * @param publisher порт публикации событий
     */
    public ExecuteTaskUseCase(TaskRepository repository, AiClient aiClient, TaskExecutionRegistry registry, TaskEventPublisher publisher) {
        this.repository = repository;
        this.aiClient = aiClient;
        this.registry = registry;
        this.publisher = publisher;
    }

    /**
     * Запускает выполнение задачи асинхронно и не блокирует вызывающий поток.
     *
     * <p>Повторный запуск той же задачи отсекается через {@link TaskExecutionRegistry}.
     * Перевод в {@code IN_PROGRESS} использует {@code task.version()}, сохраняя optimistic locking.
     *
     * @param task созданная задача, которую нужно выполнить
     */
    public void executeAsync(Task task) {
        if (!registerExecution(task)) {
            return;
        }

        startExecution(task)
                .compose(started -> runAiTask(started)
                        .compose(result -> completeTask(started, result))
                        .recover(error -> handleExecutionError(started, error).map(started)))
                .onComplete(result -> cleanupExecution(task, result.failed() ? result.cause() : null));
    }

    private boolean registerExecution(Task task) {
        if (!registry.register(task.id())) {
            log.warn("AI task execution registration skipped because task is already registered: taskId={}", task.id());
            return false;
        }
        log.info("AI task execution registered: taskId={}, clientId={}, status={}, version={}",
                task.id(), task.clientId(), task.status(), task.version());
        return true;
    }

    private Future<Task> startExecution(Task task) {
        return repository.markInProgress(task.id(), task.version())
                .onSuccess(started -> log.info("AI task started: taskId={}, clientId={}, status={}",
                        started.id(), started.clientId(), started.status()))
                .compose(started -> publishEvent(TaskEventType.STARTED, started));
    }

    private Future<String> runAiTask(Task task) {
        return aiClient.run(
                task.id().toString(),
                task.prompt(),
                progress -> handleProgress(task, progress),
                () -> registry.isCancelled(task.id()));
    }

    private Future<Void> handleProgress(Task task, int progress) {
        if (registry.isCancelled(task.id())) {
            log.info("AI task progress ignored after cancellation: taskId={}, progress={}", task.id(), progress);
            return Future.succeededFuture();
        }
        return repository.updateProgress(task.id(), progress)
                .onSuccess(updated -> log.debug("AI task progress updated: taskId={}, progress={}, status={}, version={}",
                        updated.id(), updated.progress(), updated.status(), updated.version()))
                .compose(updated -> publishEvent(TaskEventType.PROGRESS, updated).mapEmpty());
    }

    private Future<Task> completeTask(Task task, String result) {
        return repository.complete(task.id(), TaskStatus.COMPLETED, result, null)
                .onSuccess(done -> log.info("AI task completed: taskId={}, clientId={}, status={}",
                        done.id(), done.clientId(), done.status()))
                .compose(this::publishTerminalEvent);
    }

    private Future<Void> handleExecutionError(Task task, Throwable error) {
        if (registry.isCancelled(task.id())) {
            log.info("AI task execution stopped after cancellation: taskId={}", task.id());
            return Future.succeededFuture();
        }

        TaskStatus status = resolveFailureStatus(error);
        return repository.complete(task.id(), status, null, safeReason(error))
                .onSuccess(done -> logTerminalFailure(done, error))
                .compose(this::publishTerminalEvent)
                .mapEmpty();
    }

    private TaskStatus resolveFailureStatus(Throwable error) {
        return error instanceof AiTimeoutException ? TaskStatus.TIMED_OUT : TaskStatus.FAILED;
    }

    private Future<Task> publishEvent(TaskEventType eventType, Task task) {
        return publisher.publish(eventType, task).map(task);
    }

    private Future<Task> publishTerminalEvent(Task task) {
        TaskEventType eventType = TaskEventType.fromTerminalStatus(task.status());
        return publishEvent(eventType, task)
                .recover(error -> {
                    log.error("AI task terminal event publication failed: taskId={}, status={}, eventType={}, reason={}",
                            task.id(), task.status(), eventType.wireType(), safeReason(error), error);
                    return Future.succeededFuture(task);
                });
    }

    private void cleanupExecution(Task task, Throwable error) {
        try {
            if (error != null) {
                logAsyncChainFailure(task, error);
            }
        } finally {
            registry.unregister(task.id());
            log.debug("AI task execution unregistered: taskId={}", task.id());
        }
    }

    private void logTerminalFailure(Task task, Throwable error) {
        if (task.status() == TaskStatus.TIMED_OUT) {
            log.warn("AI task timed out: taskId={}, clientId={}, status={}, reason={}",
                    task.id(), task.clientId(), task.status(), safeReason(error));
            return;
        }
        log.error("AI task failed: taskId={}, clientId={}, status={}, reason={}",
                task.id(), task.clientId(), task.status(), safeReason(error), error);
    }

    private void logAsyncChainFailure(Task task, Throwable error) {
        if (error instanceof TaskConflictException) {
            log.warn("AI task asynchronous chain stopped by optimistic locking conflict: taskId={}, reason={}",
                    task.id(), safeReason(error));
            return;
        }
        log.error("Unexpected error in AI task asynchronous chain: taskId={}", task.id(), error);
    }

    private String safeReason(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
