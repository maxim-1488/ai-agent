package ru.spb.aiagent.application.usecase;

import ru.spb.aiagent.application.core.TaskRepository;
import ru.spb.aiagent.domain.model.Task;
import io.vertx.core.Future;
import java.util.UUID;

/**
 * Use case чтения задачи с обязательной изоляцией по X-Client-Id.
 */
public class GetTaskUseCase {
    private final TaskRepository repository;

    /**
     * Создаёт use case.
     */
    public GetTaskUseCase(TaskRepository repository) {
        this.repository = repository;
    }

    /**
     * Возвращает задачу текущего клиента или not found.
     */
    public Future<Task> get(String clientId, UUID taskId) {
        return repository.findByIdAndClientId(taskId, clientId);
    }
}
