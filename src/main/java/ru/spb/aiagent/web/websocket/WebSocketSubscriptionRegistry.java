package ru.spb.aiagent.web.websocket;

import ru.spb.aiagent.application.core.TaskEventPublisher;
import ru.spb.aiagent.domain.model.Task;
import ru.spb.aiagent.web.mapper.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
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
    private final Map<ServerWebSocket, HeartbeatState> heartbeats = new ConcurrentHashMap<>();
    private final ObjectMapper json = JsonMapper.create();
    private final Clock clock;

    /**
     * Создаёт registry с системными часами.
     */
    public WebSocketSubscriptionRegistry() {
        this(Clock.systemUTC());
    }

    WebSocketSubscriptionRegistry(Clock clock) {
        this.clock = clock;
    }

    /**
     * Регистрирует новое соединение клиента, если socket ещё открыт.
     *
     * @param socket WebSocket-соединение
     * @param clientId идентификатор клиента
     * @return true, если соединение зарегистрировано
     */
    public synchronized boolean register(ServerWebSocket socket, String clientId) {
        if (socket.isClosed()) {
            log.debug("Skip closed WebSocket registration: clientId={}", clientId);
            return false;
        }
        log.debug("Register WebSocket connection: clientId={}", clientId);
        clients.put(socket, clientId);
        bySocket.put(socket, ConcurrentHashMap.newKeySet());
        heartbeats.put(socket, new HeartbeatState(0));
        socket.pongHandler(ignored -> recordPong(socket));
        socket.closeHandler(v -> unregister(socket));
        return true;
    }

    /**
     * Добавляет подписку idempotent-образом только для активного зарегистрированного соединения.
     *
     * <p>Проверка активности и добавление подписки выполняются как одна операция registry:
     * поздний callback после {@link #unregister(ServerWebSocket)} не может создать записи
     * {@code connection -> taskIds} или {@code taskId -> connection} заново.
     *
     * @param socket WebSocket-соединение
     * @param taskId идентификатор задачи
     * @return true, если подписка существует после вызова
     */
    public synchronized boolean subscribe(ServerWebSocket socket, UUID taskId) {
        String clientId = clients.get(socket);
        Set<UUID> tasks = bySocket.get(socket);
        if (clientId == null || tasks == null) {
            log.debug("Skipping WebSocket subscription because connection is no longer active: taskId={}", taskId);
            return false;
        }
        if (socket.isClosed()) {
            log.debug("Skipping WebSocket subscription because socket is closed: clientId={}, taskId={}", clientId, taskId);
            removeConnection(socket);
            return false;
        }
        log.debug("Add WebSocket subscription: clientId={}, taskId={}", clientId, taskId);
        tasks.add(taskId);
        byTask.computeIfAbsent(taskId, id -> ConcurrentHashMap.newKeySet()).add(socket);
        return true;
    }

    /**
     * Удаляет подписку. Повторный вызов для той же пары socket/task безопасен.
     *
     * @param socket WebSocket-соединение
     * @param taskId идентификатор задачи
     */
    public synchronized void unsubscribe(ServerWebSocket socket, UUID taskId) {
        log.debug("Remove WebSocket subscription: clientId={}, taskId={}", clients.get(socket), taskId);
        Set<UUID> tasks = bySocket.get(socket);
        if (tasks != null) {
            tasks.remove(taskId);
        }
        Set<ServerWebSocket> sockets = byTask.get(taskId);
        if (sockets != null) {
            sockets.remove(socket);
            if (sockets.isEmpty()) {
                byTask.remove(taskId);
            }
        }
    }

    /**
     * Удаляет все подписки соединения.
     *
     * @param socket WebSocket-соединение
     */
    public synchronized void unregister(ServerWebSocket socket) {
        removeConnection(socket);
    }

    private void removeConnection(ServerWebSocket socket) {
        String clientId = clients.get(socket);
        Set<UUID> tasks = bySocket.remove(socket);
        if (tasks != null) {
            tasks.forEach(taskId -> {
                Set<ServerWebSocket> sockets = byTask.get(taskId);
                if (sockets != null) {
                    sockets.remove(socket);
                    if (sockets.isEmpty()) {
                        byTask.remove(taskId);
                    }
                }
            });
        }
        clients.remove(socket);
        heartbeats.remove(socket);
        log.debug("Unregistered WebSocket connection and cleaned subscriptions: clientId={}, subscriptionCount={}",
                clientId, tasks == null ? 0 : tasks.size());
    }

    /**
     * Закрывает все активные соединения при shutdown.
     */
    public void closeAll() {
        Set<ServerWebSocket> sockets;
        synchronized (this) {
            log.info("Closing WebSocket connections: count={}", clients.size());
            sockets = Set.copyOf(clients.keySet());
            clients.clear();
            byTask.clear();
            bySocket.clear();
            heartbeats.clear();
        }
        sockets.forEach(ServerWebSocket::close);
    }

    /**
     * Публикует событие всем активным подписчикам задачи с backpressure-проверкой.
     */
    @Override
    public Future<Void> publish(String type, Task task) {
        Set<ServerWebSocket> sockets;
        synchronized (this) {
            sockets = new HashSet<>(byTask.getOrDefault(task.id(), Set.of()));
            sockets.removeIf(socket -> !clients.containsKey(socket));
        }
        log.debug("Publishing WebSocket task event: type={}, taskId={}, subscriberCount={}", type, task.id(), sockets.size());
        sockets.forEach(socket -> send(socket, Map.of("type", type, "task", task)));
        return Future.succeededFuture();
    }

    /**
     * Отправляет одно сообщение активному соединению, если write queue не переполнена.
     *
     * @param socket WebSocket-соединение
     * @param message сообщение, сериализуемое в JSON
     */
    public synchronized void send(ServerWebSocket socket, Object message) {
        try {
            if (!isActive(socket)) {
                log.debug("Skip WebSocket send because connection is no longer active");
                return;
            }
            if (socket.writeQueueFull()) {
                log.warn("WebSocket write queue full, closing slow client: clientId={}", clients.get(socket));
                socket.close();
                unregister(socket);
                return;
            }
            socket.writeTextMessage(json.writeValueAsString(message));
        } catch (Exception e) {
            log.warn("Failed to send WebSocket message, closing connection: clientId={}", clients.get(socket), e);
            socket.close();
            unregister(socket);
        }
    }

    /**
     * Возвращает true, если соединение зарегистрировано в registry и сам socket ещё открыт.
     *
     * @param socket WebSocket-соединение
     * @return признак активного lifecycle-состояния соединения
     */
    public synchronized boolean isActive(ServerWebSocket socket) {
        return clients.containsKey(socket) && bySocket.containsKey(socket) && !socket.isClosed();
    }

    /**
     * Выполняет heartbeat-проверку всех активных WebSocket-соединений.
     *
     * <p>Соединения, которые не ответили pong на предыдущий ping за {@code timeoutMs}, закрываются и полностью
     * удаляются из registry вместе с подписками. Остальным соединениям отправляется WebSocket ping с timestamp.
     *
     * @param timeoutMs максимальное время ожидания pong после ping в миллисекундах
     */
    public void heartbeat(long timeoutMs) {
        heartbeat(clock.millis(), timeoutMs);
    }

    synchronized void heartbeat(long nowMs, long timeoutMs) {
        Map<ServerWebSocket, Long> pings = new HashMap<>();
        var timedOut = new ArrayList<ServerWebSocket>();
        heartbeats.forEach((socket, state) -> {
            if (!isActive(socket) || state.waitingForPong(nowMs, timeoutMs)) {
                timedOut.add(socket);
            } else if (!state.awaitingPong()) {
                state.markPingSent(nowMs);
                pings.put(socket, nowMs);
            }
        });
        timedOut.forEach(socket -> {
            log.warn("WebSocket heartbeat timeout, closing connection: clientId={}", clients.get(socket));
            socket.close();
            unregister(socket);
        });
        pings.forEach(this::writePing);
    }

    private void recordPong(ServerWebSocket socket) {
        synchronized (this) {
            HeartbeatState state = heartbeats.get(socket);
            if (state != null) {
                state.markPongReceived();
            }
        }
    }

    private void writePing(ServerWebSocket socket, long timestampMs) {
        try {
            Future<Void> write = socket.writePing(Buffer.buffer(Long.toString(timestampMs), StandardCharsets.UTF_8.name()));
            if (write != null) {
                write.onFailure(error -> {
                    log.warn("Failed to send WebSocket heartbeat ping, closing connection: clientId={}", clients.get(socket), error);
                    socket.close();
                    unregister(socket);
                });
            }
        } catch (Exception e) {
            log.warn("Failed to send WebSocket heartbeat ping, closing connection: clientId={}", clients.get(socket), e);
            socket.close();
            unregister(socket);
        }
    }

    boolean containsConnection(ServerWebSocket socket) {
        return clients.containsKey(socket);
    }

    boolean containsSocketSubscriptions(ServerWebSocket socket) {
        return bySocket.containsKey(socket);
    }

    boolean hasSubscription(ServerWebSocket socket, UUID taskId) {
        Set<ServerWebSocket> sockets = byTask.get(taskId);
        return sockets != null && sockets.contains(socket);
    }

    int subscriberCount(UUID taskId) {
        return byTask.getOrDefault(taskId, Set.of()).size();
    }

    private static final class HeartbeatState {
        private long lastPingAtMs;
        private boolean awaitingPong;

        private HeartbeatState(long lastPingAtMs) {
            this.lastPingAtMs = lastPingAtMs;
        }

        private boolean awaitingPong() {
            return awaitingPong;
        }

        private boolean waitingForPong(long nowMs, long timeoutMs) {
            return awaitingPong() && nowMs - lastPingAtMs >= timeoutMs;
        }

        private void markPingSent(long timestampMs) {
            lastPingAtMs = timestampMs;
            awaitingPong = true;
        }

        private void markPongReceived() {
            awaitingPong = false;
        }
    }
}
