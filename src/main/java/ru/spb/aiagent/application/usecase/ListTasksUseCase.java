package ru.spb.aiagent.application.usecase;

import ru.spb.aiagent.application.core.TaskRepository;
import ru.spb.aiagent.domain.model.TaskFilter;
import ru.spb.aiagent.domain.model.TaskPage;
import io.vertx.core.Future;

/**
 * Use case получения страницы задач клиента.
 */
public class ListTasksUseCase {
    private final TaskRepository repository;

    /**
     * Создаёт use case.
     */
    public ListTasksUseCase(TaskRepository repository) {
        this.repository = repository;
    }

    /**
     * Возвращает страницу задач только для текущего клиента.
     */
    public Future<TaskPage> list(String clientId, TaskFilter filter) {
        return repository.list(clientId, filter);
    }
}
