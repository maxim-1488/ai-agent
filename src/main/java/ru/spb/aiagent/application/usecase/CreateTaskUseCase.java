package ru.spb.aiagent.application.usecase;

import ru.spb.aiagent.application.core.ClockProvider;
import ru.spb.aiagent.application.core.TaskEventPublisher;
import ru.spb.aiagent.application.core.TaskRepository;
import ru.spb.aiagent.domain.model.Task;
import io.vertx.core.Future;

/**
 * Use case создания AI-задачи, сохранения и запуска асинхронного выполнения.
 */
public class CreateTaskUseCase {
    private final TaskRepository repository;
    private final ClockProvider clock;
    private final ExecuteTaskUseCase executor;
    private final TaskEventPublisher publisher;

    /**
     * Создаёт use case.
     */
    public CreateTaskUseCase(TaskRepository repository, ClockProvider clock, ExecuteTaskUseCase executor, TaskEventPublisher publisher) {
        this.repository = repository;
        this.clock = clock;
        this.executor = executor;
        this.publisher = publisher;
    }

    /**
     * Валидирует вход, создаёт задачу и планирует выполнение.
     */
    public Future<Task> create(String clientId, String prompt) {
        if (clientId == null || clientId.isBlank()) {
            return Future.failedFuture(new IllegalArgumentException("X-Client-Id is required"));
        }
        if (prompt == null || prompt.isBlank()) {
            return Future.failedFuture(new IllegalArgumentException("prompt is required"));
        }
        Task task = Task.create(clientId.trim(), prompt.trim(), clock.now());
        return repository.create(task)
                .compose(saved -> publisher.publish("TASK_CREATED", saved).map(saved))
                .onSuccess(saved -> executor.executeAsync(saved));
    }
}
