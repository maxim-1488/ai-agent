package ru.spb.aiagent.application.usecase;

import ru.spb.aiagent.application.core.ClockProvider;
import ru.spb.aiagent.application.core.TaskEventPublisher;
import ru.spb.aiagent.application.core.TaskEventType;
import ru.spb.aiagent.application.core.TaskRepository;
import ru.spb.aiagent.domain.model.Task;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use case создания AI-задачи, сохранения и запуска асинхронного выполнения.
 */
public class CreateTaskUseCase {
    private static final Logger log = LoggerFactory.getLogger(CreateTaskUseCase.class);

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
        log.debug("Starting create AI task use case: clientId={}, promptLength={}", clientId, prompt == null ? 0 : prompt.length());
        if (clientId == null || clientId.isBlank()) {
            return Future.failedFuture(new IllegalArgumentException("X-Client-Id is required"));
        }
        if (prompt == null || prompt.isBlank()) {
            return Future.failedFuture(new IllegalArgumentException("prompt is required"));
        }
        Task task = Task.create(clientId.trim(), prompt.trim(), clock.now());
        return repository.create(task)
                .compose(saved -> publisher.publish(TaskEventType.CREATED, saved).map(saved))
                .onSuccess(saved -> {
                    log.info("AI task created: taskId={}, clientId={}, status={}, promptLength={}",
                            saved.id(), saved.clientId(), saved.status(), saved.prompt().length());
                    executor.executeAsync(saved);
                });
    }
}
