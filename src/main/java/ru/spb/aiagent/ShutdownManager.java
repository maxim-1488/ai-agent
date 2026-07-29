package ru.spb.aiagent;

import ru.spb.aiagent.web.websocket.HeartbeatService;
import ru.spb.aiagent.web.websocket.WebSocketSubscriptionRegistry;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Graceful shutdown manager: закрывает WebSocket, timers, PgPool и Vert.x.
 */
public class ShutdownManager {
    private static final Logger log = LoggerFactory.getLogger(ShutdownManager.class);

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
        CountDownLatch shutdownCompleted = new CountDownLatch(1);
        log.info("Graceful shutdown initiated");
        log.info("Stopping heartbeat and AI timers");
        heartbeat.stop();
        log.info("Closing WebSocket connections");
        registry.closeAll();
        log.info("Closing PostgreSQL pool");
        pool.close()
                .onSuccess(v -> {
                    log.info("PostgreSQL pool closed");
                    log.info("Stopping Vert.x");
                    vertx.close()
                            .onSuccess(done -> log.info("Graceful shutdown completed"))
                            .onFailure(error -> log.error("Failed to stop Vert.x during graceful shutdown", error))
                            .onComplete(done -> shutdownCompleted.countDown());
                })
                .onFailure(error -> {
                    log.error("Failed to close PostgreSQL pool during graceful shutdown", error);
                    shutdownCompleted.countDown();
                });
        awaitShutdown(shutdownCompleted);
    }

    private void awaitShutdown(CountDownLatch shutdownCompleted) {
        try {
            if (!shutdownCompleted.await(10, TimeUnit.SECONDS)) {
                log.warn("Graceful shutdown did not complete within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Graceful shutdown wait was interrupted");
        }
    }
}
