package ru.spb.aiagent.web.websocket;

import ru.spb.aiagent.application.core.TaskEventPublisher;
import ru.spb.aiagent.domain.model.Task;
import ru.spb.aiagent.web.mapper.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import io.vertx.core.http.ServerWebSocket;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory registry WebSocket-подписок.
 *
 * <p>Поддерживает несколько соединений клиента, несколько задач на соединение, несколько
 * подписчиков одной задачи. Не является distributed registry между backend instances.
 */
public class WebSocketSubscriptionRegistry implements TaskEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(WebSocketSubscriptionRegistry.class);

    private final Map<ServerWebSocket, String> clients = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ServerWebSocket>> byTask = new ConcurrentHashMap<>();
    private final Map<ServerWebSocket, Set<UUID>> bySocket = new ConcurrentHashMap<>();
    private final ObjectMapper json = JsonMapper.create();

    /**
     * Регистрирует новое соединение клиента.
     */
    public void register(ServerWebSocket socket, String clientId) {
        log.debug("Register WebSocket connection: clientId={}", clientId);
        clients.put(socket, clientId);
        bySocket.put(socket, ConcurrentHashMap.newKeySet());
        socket.closeHandler(v -> unregister(socket));
    }

    /**
     * Добавляет подписку idempotent-образом.
     */
    public void subscribe(ServerWebSocket socket, UUID taskId) {
        log.debug("Add WebSocket subscription: clientId={}, taskId={}", clients.get(socket), taskId);
        bySocket.computeIfAbsent(socket, s -> ConcurrentHashMap.newKeySet()).add(taskId);
        byTask.computeIfAbsent(taskId, id -> ConcurrentHashMap.newKeySet()).add(socket);
    }

    /**
     * Удаляет подписку.
     */
    public void unsubscribe(ServerWebSocket socket, UUID taskId) {
        log.debug("Remove WebSocket subscription: clientId={}, taskId={}", clients.get(socket), taskId);
        Set<UUID> tasks = bySocket.get(socket);
        if (tasks != null) {
            tasks.remove(taskId);
        }
        Set<ServerWebSocket> sockets = byTask.get(taskId);
        if (sockets != null) {
            sockets.remove(socket);
        }
    }

    /**
     * Удаляет все подписки соединения.
     */
    public void unregister(ServerWebSocket socket) {
        String clientId = clients.get(socket);
        Set<UUID> tasks = bySocket.remove(socket);
        if (tasks != null) {
            tasks.forEach(taskId -> unsubscribe(socket, taskId));
        }
        clients.remove(socket);
        log.debug("Unregistered WebSocket connection and cleaned subscriptions: clientId={}, subscriptionCount={}",
                clientId, tasks == null ? 0 : tasks.size());
    }

    /**
     * Закрывает все активные соединения при shutdown.
     */
    public void closeAll() {
        log.info("Closing WebSocket connections: count={}", clients.size());
        clients.keySet().forEach(ServerWebSocket::close);
        clients.clear();
        byTask.clear();
        bySocket.clear();
    }

    /**
     * Публикует событие всем подписчикам задачи с backpressure-проверкой.
     */
    @Override
    public Future<Void> publish(String type, Task task) {
        Set<ServerWebSocket> sockets = byTask.getOrDefault(task.id(), Set.of());
        log.debug("Publishing WebSocket task event: type={}, taskId={}, subscriberCount={}", type, task.id(), sockets.size());
        sockets.forEach(socket -> send(socket, Map.of("type", type, "task", task)));
        return Future.succeededFuture();
    }

    /**
     * Отправляет одно сообщение, если write queue не переполнена.
     */
    public void send(ServerWebSocket socket, Object message) {
        try {
            if (socket.writeQueueFull()) {
                log.warn("WebSocket write queue full, closing slow client: clientId={}", clients.get(socket));
                socket.close();
                return;
            }
            socket.writeTextMessage(json.writeValueAsString(message));
        } catch (Exception e) {
            log.warn("Failed to send WebSocket message, closing connection: clientId={}", clients.get(socket), e);
            socket.close();
        }
    }
}
