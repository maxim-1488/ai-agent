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

/**
 * In-memory registry WebSocket-подписок.
 *
 * <p>Поддерживает несколько соединений клиента, несколько задач на соединение, несколько
 * подписчиков одной задачи. Не является distributed registry между backend instances.
 */
public class WebSocketSubscriptionRegistry implements TaskEventPublisher {
    private final Map<ServerWebSocket, String> clients = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ServerWebSocket>> byTask = new ConcurrentHashMap<>();
    private final Map<ServerWebSocket, Set<UUID>> bySocket = new ConcurrentHashMap<>();
    private final ObjectMapper json = JsonMapper.create();

    /**
     * Регистрирует новое соединение клиента.
     */
    public void register(ServerWebSocket socket, String clientId) {
        clients.put(socket, clientId);
        bySocket.put(socket, ConcurrentHashMap.newKeySet());
        socket.closeHandler(v -> unregister(socket));
    }

    /**
     * Добавляет подписку idempotent-образом.
     */
    public void subscribe(ServerWebSocket socket, UUID taskId) {
        bySocket.computeIfAbsent(socket, s -> ConcurrentHashMap.newKeySet()).add(taskId);
        byTask.computeIfAbsent(taskId, id -> ConcurrentHashMap.newKeySet()).add(socket);
    }

    /**
     * Удаляет подписку.
     */
    public void unsubscribe(ServerWebSocket socket, UUID taskId) {
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
        Set<UUID> tasks = bySocket.remove(socket);
        if (tasks != null) {
            tasks.forEach(taskId -> unsubscribe(socket, taskId));
        }
        clients.remove(socket);
    }

    /**
     * Закрывает все активные соединения при shutdown.
     */
    public void closeAll() {
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
        sockets.forEach(socket -> send(socket, Map.of("type", type, "task", task)));
        return Future.succeededFuture();
    }

    /**
     * Отправляет одно сообщение, если write queue не переполнена.
     */
    public void send(ServerWebSocket socket, Object message) {
        try {
            if (socket.writeQueueFull()) {
                socket.close();
                return;
            }
            socket.writeTextMessage(json.writeValueAsString(message));
        } catch (Exception e) {
            socket.close();
        }
    }
}
