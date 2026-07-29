package ru.spb.aiagent.web.websocket;

import io.vertx.core.http.ServerWebSocket;

/**
 * Политика backpressure: при переполнении write queue соединение закрывается.
 */
public class WebSocketBackpressurePolicy {
    /**
     * Проверяет возможность записи.
     */
    public boolean canWrite(ServerWebSocket socket) {
        return !socket.writeQueueFull();
    }
}
