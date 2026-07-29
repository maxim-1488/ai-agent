package ru.spb.aiagent.web.websocket;

import io.vertx.core.Vertx;

/**
 * Heartbeat-сервис для периодической отправки PING/PONG-событий клиентам.
 */
public class HeartbeatService {
    private final Vertx vertx;
    private long timerId = -1;

    /**
     * Создаёт heartbeat-сервис.
     */
    public HeartbeatService(Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * Запускает периодический timer.
     */
    public void start(Runnable tick) {
        timerId = vertx.setPeriodic(30_000, id -> tick.run());
    }

    /**
     * Останавливает timer при shutdown.
     */
    public void stop() {
        if (timerId >= 0) {
            vertx.cancelTimer(timerId);
        }
    }
}
