package ru.spb.aiagent.web.rest;

import io.vertx.ext.web.RoutingContext;

/**
 * Извлекает и валидирует X-Client-Id из REST/WebSocket запросов.
 */
public class ClientIdExtractor {
    /**
     * Возвращает clientId или бросает validation exception.
     */
    public String extract(RoutingContext ctx) {
        String clientId = ctx.request().getHeader("X-Client-Id");
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("X-Client-Id is required");
        }
        return clientId.trim();
    }
}
