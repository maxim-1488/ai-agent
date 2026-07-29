package ru.spb.aiagent.infrastructure.db;

import ru.spb.aiagent.infrastructure.config.AppConfig;
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
    public Pool create(Vertx vertx, AppConfig config) {
        PgConnectOptions connect = new PgConnectOptions()
                .setHost(config.databaseHost())
                .setPort(config.databasePort())
                .setDatabase(config.databaseName())
                .setUser(config.databaseUser())
                .setPassword(config.databasePassword());
        return PgBuilder.pool()
                .using(vertx)
                .connectingTo(connect)
                .with(new PoolOptions().setMaxSize(config.databasePoolSize()))
                .build();
    }
}
