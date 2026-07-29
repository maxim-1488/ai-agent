package ru.spb.aiagent.web.rest;

import ru.spb.aiagent.application.usecase.CancelTaskUseCase;
import ru.spb.aiagent.application.usecase.CreateTaskUseCase;
import ru.spb.aiagent.application.usecase.GetTaskUseCase;
import ru.spb.aiagent.application.usecase.ListTasksUseCase;
import ru.spb.aiagent.domain.model.TaskFilter;
import ru.spb.aiagent.domain.model.TaskStatus;
import ru.spb.aiagent.web.dto.CreateTaskRequest;
import ru.spb.aiagent.web.mapper.JsonMapper;
import ru.spb.aiagent.web.mapper.RestDtoMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.ext.web.RoutingContext;
import java.util.UUID;

/**
 * REST adapter задач: create, get, list, cancel.
 */
public class TaskRestHandler {
    private final CreateTaskUseCase create;
    private final GetTaskUseCase get;
    private final ListTasksUseCase list;
    private final CancelTaskUseCase cancel;
    private final ObjectMapper json = JsonMapper.create();
    private final RestDtoMapper mapper = new RestDtoMapper();
    private final ClientIdExtractor clientIds = new ClientIdExtractor();

    /**
     * Создаёт REST handler.
     */
    public TaskRestHandler(CreateTaskUseCase create, GetTaskUseCase get, ListTasksUseCase list, CancelTaskUseCase cancel) {
        this.create = create;
        this.get = get;
        this.list = list;
        this.cancel = cancel;
    }

    /**
     * Обрабатывает POST /api/v1/tasks.
     */
    public void create(RoutingContext ctx) {
        try {
            CreateTaskRequest request = json.readValue(ctx.body().asString(), CreateTaskRequest.class);
            create.create(clientIds.extract(ctx), request.prompt())
                    .onSuccess(task -> write(ctx, 201, mapper.toResponse(task)))
                    .onFailure(ctx::fail);
        } catch (Exception e) {
            ctx.fail(e);
        }
    }

    /**
     * Обрабатывает GET /api/v1/tasks/{taskId}.
     */
    public void get(RoutingContext ctx) {
        try {
            get.get(clientIds.extract(ctx), UUID.fromString(ctx.pathParam("taskId")))
                    .onSuccess(task -> write(ctx, 200, mapper.toResponse(task)))
                    .onFailure(ctx::fail);
        } catch (Exception e) {
            ctx.fail(e);
        }
    }

    /**
     * Обрабатывает GET /api/v1/tasks.
     */
    public void list(RoutingContext ctx) {
        try {
            TaskFilter filter = new TaskFilter(
                    intQuery(ctx, "page", 0),
                    intQuery(ctx, "size", 20),
                    enumQuery(ctx, "status", TaskStatus.class),
                    sort(ctx.queryParam("sort").isEmpty() ? "createdAt" : ctx.queryParam("sort").getFirst()),
                    direction(ctx.queryParam("direction").isEmpty() ? "DESC" : ctx.queryParam("direction").getFirst()));
            list.list(clientIds.extract(ctx), filter)
                    .onSuccess(page -> write(ctx, 200, mapper.toList(page)))
                    .onFailure(ctx::fail);
        } catch (Exception e) {
            ctx.fail(e);
        }
    }

    /**
     * Обрабатывает POST /api/v1/tasks/{taskId}/cancel.
     */
    public void cancel(RoutingContext ctx) {
        try {
            cancel.cancel(clientIds.extract(ctx), UUID.fromString(ctx.pathParam("taskId")))
                    .onSuccess(task -> write(ctx, 200, mapper.toResponse(task)))
                    .onFailure(ctx::fail);
        } catch (Exception e) {
            ctx.fail(e);
        }
    }

    private void write(RoutingContext ctx, int status, Object body) {
        try {
            ctx.response().setStatusCode(status).putHeader("Content-Type", "application/json").end(json.writeValueAsString(body));
        } catch (Exception e) {
            ctx.fail(e);
        }
    }

    private int intQuery(RoutingContext ctx, String name, int fallback) {
        return ctx.queryParam(name).isEmpty() ? fallback : Integer.parseInt(ctx.queryParam(name).getFirst());
    }

    private <E extends Enum<E>> E enumQuery(RoutingContext ctx, String name, Class<E> type) {
        return ctx.queryParam(name).isEmpty() ? null : Enum.valueOf(type, ctx.queryParam(name).getFirst());
    }

    private TaskFilter.SortField sort(String raw) {
        return switch (raw) {
            case "updatedAt" -> TaskFilter.SortField.UPDATED_AT;
            case "status" -> TaskFilter.SortField.STATUS;
            case "progress" -> TaskFilter.SortField.PROGRESS;
            default -> TaskFilter.SortField.CREATED_AT;
        };
    }

    private TaskFilter.SortDirection direction(String raw) {
        return "ASC".equalsIgnoreCase(raw) ? TaskFilter.SortDirection.ASC : TaskFilter.SortDirection.DESC;
    }
}
