package ru.spb.aiagent.web.websocket;

import ru.spb.aiagent.application.usecase.GetTaskUseCase;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.web.RoutingContext;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final int DEFAULT_MAX_MESSAGE_SIZE_BYTES = 8192;
    private static final short CLOSE_CODE_MESSAGE_TOO_BIG = 1009;

    private final GetTaskUseCase getTask;
    private final WebSocketSubscriptionRegistry registry;
    private final WebSocketMessageParser parser;
    private final int maxMessageSizeBytes;

    /**
     * Создаёт WebSocket handler.
     */
    public TaskWebSocketHandler(GetTaskUseCase getTask, WebSocketSubscriptionRegistry registry) {
        this(getTask, registry, DEFAULT_MAX_MESSAGE_SIZE_BYTES);
    }

    /**
     * Создаёт WebSocket handler с ограничением размера входящих сообщений в байтах.
     */
    public TaskWebSocketHandler(GetTaskUseCase getTask, WebSocketSubscriptionRegistry registry, int maxMessageSizeBytes) {
        this(getTask, registry, new WebSocketMessageParser(), maxMessageSizeBytes);
    }

    TaskWebSocketHandler(GetTaskUseCase getTask, WebSocketSubscriptionRegistry registry,
                         WebSocketMessageParser parser, int maxMessageSizeBytes) {
        if (maxMessageSizeBytes <= 0) {
            throw new IllegalArgumentException("maxMessageSizeBytes must be positive");
        }
        this.getTask = getTask;
        this.registry = registry;
        this.parser = parser;
        this.maxMessageSizeBytes = maxMessageSizeBytes;
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
        AtomicBoolean closingAfterSizeViolation = new AtomicBoolean(false);
        ws.exceptionHandler(error -> {
            if (isOversizedWebSocketException(error)) {
                closingAfterSizeViolation.set(true);
                log.warn("Oversized WebSocket frame rejected by Vert.x: clientId={}, remoteAddress={}, maxMessageSizeBytes={}",
                        clientId, ws.remoteAddress(), maxMessageSizeBytes);
                ws.close(CLOSE_CODE_MESSAGE_TOO_BIG, "Message too big");
                registry.unregister(ws);
                return;
            }
            if (closingAfterSizeViolation.get() && isClosedWebSocketException(error)) {
                log.debug("WebSocket closed after oversized frame rejection: clientId={}, remoteAddress={}", clientId, ws.remoteAddress());
                return;
            }
            log.warn("Unexpected WebSocket error: clientId={}, remoteAddress={}", clientId, ws.remoteAddress(), error);
        });
        ws.textMessageHandler(raw -> {
            try {
                int payloadSizeBytes = raw == null ? 0 : raw.getBytes(StandardCharsets.UTF_8).length;
                if (payloadSizeBytes > maxMessageSizeBytes) {
                    closingAfterSizeViolation.set(true);
                    log.warn("Oversized WebSocket message rejected: clientId={}, remoteAddress={}, payloadSizeBytes={}, maxMessageSizeBytes={}",
                            clientId, ws.remoteAddress(), payloadSizeBytes, maxMessageSizeBytes);
                    ws.close(CLOSE_CODE_MESSAGE_TOO_BIG, "Message too big");
                    registry.unregister(ws);
                    return;
                }
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

    private boolean isOversizedWebSocketException(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return error != null
                && "CorruptedWebSocketFrameException".equals(error.getClass().getSimpleName())
                && message != null
                && message.startsWith("Max ")
                && (message.contains("frame") || message.contains("message"));
    }

    private boolean isClosedWebSocketException(Throwable error) {
        return error != null && "HttpClosedException".equals(error.getClass().getSimpleName());
    }
}
