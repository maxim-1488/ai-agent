package ru.spb.aiagent.infrastructure.db;

import ru.spb.aiagent.infrastructure.config.DatabaseConfig;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

/**
 * Фабрика единственного PgPool, используемого всеми repository-адаптерами.
 */
public class PgPoolFactory {
    /**
     * Создаёт PgPool после успешных Liquibase-миграций.
     */
    public Pool create(Vertx vertx, DatabaseConfig config) {
        PgConnectOptions connect = new PgConnectOptions()
                .setHost(config.host())
                .setPort(config.port())
                .setDatabase(config.name())
                .setUser(config.user())
                .setPassword(config.password());
        return PgBuilder.pool()
                .using(vertx)
                .connectingTo(connect)
                .with(new PoolOptions().setMaxSize(config.poolSize()))
                .build();
    }
}
