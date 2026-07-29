package ru.spb.aiagent;

import ru.spb.aiagent.web.websocket.HeartbeatService;
import ru.spb.aiagent.web.websocket.WebSocketSubscriptionRegistry;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;

/**
 * Graceful shutdown manager: закрывает WebSocket, timers, PgPool и Vert.x.
 */
public class ShutdownManager {
    private final Vertx vertx;
    private final Pool pool;
    private final WebSocketSubscriptionRegistry registry;
    private final HeartbeatService heartbeat;

    /**
     * Создаёт manager shutdown.
     */
    public ShutdownManager(Vertx vertx, Pool pool, WebSocketSubscriptionRegistry registry, HeartbeatService heartbeat) {
        this.vertx = vertx;
        this.pool = pool;
        this.registry = registry;
        this.heartbeat = heartbeat;
    }

    /**
     * Регистрирует JVM shutdown hook.
     */
    public void register() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    /**
     * Выполняет закрытие ресурсов.
     */
    public void shutdown() {
        heartbeat.stop();
        registry.closeAll();
        pool.close().onComplete(v -> vertx.close());
    }
}
