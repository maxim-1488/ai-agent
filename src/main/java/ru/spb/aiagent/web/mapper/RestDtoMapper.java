package ru.spb.aiagent.web.mapper;

import ru.spb.aiagent.domain.model.Task;
import ru.spb.aiagent.domain.model.TaskPage;
import ru.spb.aiagent.web.dto.TaskListResponse;
import ru.spb.aiagent.web.dto.TaskResponse;

/**
 * Mapper доменной модели в REST DTO.
 */
public class RestDtoMapper {
    /**
     * Преобразует задачу в DTO.
     */
    public TaskResponse toResponse(Task task) {
        return new TaskResponse(task.id(), task.prompt(), task.status(), task.progress(), task.result(), task.errorMessage(),
                task.createdAt(), task.startedAt(), task.completedAt(), task.updatedAt(), task.version(),
                "/api/v1/tasks/" + task.id(), "/ws/tasks");
    }

    /**
     * Преобразует страницу задач в DTO.
     */
    public TaskListResponse toList(TaskPage page) {
        return new TaskListResponse(page.items().stream().map(this::toResponse).toList(), page.total(), page.page(), page.size());
    }
}
