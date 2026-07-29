package ru.spb.aiagent;

import io.vertx.core.Vertx;

/**
 * Фабрика Vert.x instance для production и тестов bootstrap.
 */
public class VertxFactory {
    /**
     * Создаёт Vert.x instance.
     */
    public Vertx create() {
        return Vertx.vertx();
    }
}
