package ru.spb.aiagent.web.websocket;

import ru.spb.aiagent.application.usecase.GetTaskUseCase;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.web.RoutingContext;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket adapter endpoint /ws/tasks.
 *
 * <p>Handshake требует X-Client-Id. SUBSCRIBE проверяет принадлежность задачи клиенту,
 * повторная подписка idempotent, UNSUBSCRIBE удаляет только указанную задачу.
 */
public class TaskWebSocketHandler implements io.vertx.core.Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(TaskWebSocketHandler.class);

    private final GetTaskUseCase getTask;
    private final WebSocketSubscriptionRegistry registry;
    private final WebSocketMessageParser parser = new WebSocketMessageParser();

    /**
     * Создаёт WebSocket handler.
     */
    public TaskWebSocketHandler(GetTaskUseCase getTask, WebSocketSubscriptionRegistry registry) {
        this.getTask = getTask;
        this.registry = registry;
    }

    /**
     * Принимает WebSocket upgrade и настраивает обработку сообщений.
     */
    @Override
    public void handle(RoutingContext ctx) {
        String clientId = ctx.request().getHeader("X-Client-Id");
        if ((clientId == null || clientId.isBlank()) && !ctx.queryParam("clientId").isEmpty()) {
            clientId = ctx.queryParam("clientId").getFirst();
        }
        if (clientId == null || clientId.isBlank()) {
            log.warn("WebSocket handshake rejected: missing X-Client-Id");
            ctx.response().setStatusCode(400).end("X-Client-Id required");
            return;
        }
        String resolvedClientId = clientId.trim();
        ctx.request().toWebSocket()
                .onSuccess(ws -> bind(ws, resolvedClientId))
                .onFailure(error -> {
                    log.warn("WebSocket upgrade failed: clientId={}", resolvedClientId, error);
                    ctx.fail(error);
                });
    }

    void bind(ServerWebSocket ws, String clientId) {
        log.debug("WebSocket connection established: clientId={}, remoteAddress={}", clientId, ws.remoteAddress());
        if (!registry.register(ws, clientId)) {
            return;
        }
        ws.exceptionHandler(error -> log.warn("Unexpected WebSocket error: clientId={}, remoteAddress={}", clientId, ws.remoteAddress(), error));
        ws.textMessageHandler(raw -> {
            try {
                WebSocketMessageParser.ClientMessage message = parser.parse(raw);
                switch (message.action()) {
                    case "SUBSCRIBE" -> getTask.get(clientId, message.taskId()).onSuccess(task -> {
                        if (!registry.subscribe(ws, task.id())) {
                            log.debug("Skipping WebSocket subscribe callback because connection is no longer active: clientId={}, taskId={}",
                                    clientId, task.id());
                            return;
                        }
                        log.debug("WebSocket subscribe completed: clientId={}, taskId={}", clientId, task.id());
                        registry.send(ws, Map.of("type", "SUBSCRIBED", "taskId", task.id(), "task", task));
                    }).onFailure(error -> {
                        if (!registry.isActive(ws)) {
                            log.debug("Skipping WebSocket subscribe error because connection is no longer active: clientId={}, taskId={}",
                                    clientId, message.taskId());
                            return;
                        }
                        log.warn("WebSocket subscribe rejected: clientId={}, taskId={}, reason={}", clientId, message.taskId(),
                                error.getMessage());
                        registry.send(ws, Map.of("type", "ERROR", "message", error.getMessage()));
                    });
                    case "UNSUBSCRIBE" -> {
                        log.debug("WebSocket unsubscribe: clientId={}, taskId={}", clientId, message.taskId());
                        registry.unsubscribe(ws, message.taskId());
                        registry.send(ws, Map.of("type", "UNSUBSCRIBED", "taskId", message.taskId()));
                    }
                    case "PING" -> registry.send(ws, Map.of("type", "PONG"));
                    default -> {
                        log.warn("WebSocket unknown action: clientId={}, action={}", clientId, message.action());
                        registry.send(ws, Map.of("type", "ERROR", "message", "Unknown action"));
                    }
                }
            } catch (Exception e) {
                log.warn("Malformed WebSocket message: clientId={}, payloadLength={}", clientId, raw == null ? 0 : raw.length());
                registry.send(ws, Map.of("type", "ERROR", "message", "Invalid WebSocket message"));
            }
        });
    }
}
