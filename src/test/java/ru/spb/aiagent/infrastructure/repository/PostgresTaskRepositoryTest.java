package ru.spb.aiagent.infrastructure.repository;

import ru.spb.aiagent.domain.model.Task;
import ru.spb.aiagent.domain.model.TaskFilter;
import ru.spb.aiagent.infrastructure.liquibase.LiquibaseMigrator;
import io.vertx.core.Vertx;
import io.vertx.core.Future;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PostgresTaskRepositoryTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("ai_agent")
            .withUsername("ai_agent")
            .withPassword("ai_agent");

    @Test
    void createsAndListsTaskWithWhitelistedSort() {
        new LiquibaseMigrator().migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Vertx vertx = Vertx.vertx();
        Pool pool = PgBuilder.pool()
                .using(vertx)
                .connectingTo(new PgConnectOptions()
                .setHost(postgres.getHost())
                .setPort(postgres.getFirstMappedPort())
                .setDatabase(postgres.getDatabaseName())
                .setUser(postgres.getUsername())
                .setPassword(postgres.getPassword()))
                .with(new PoolOptions().setMaxSize(2))
                .build();
        PostgresTaskRepository repo = new PostgresTaskRepository(pool);
        Task saved = await(repo.create(Task.create("client", "prompt", OffsetDateTime.now(ZoneOffset.UTC))));
        assertThat(await(repo.findByIdAndClientId(saved.id(), "client")).id()).isEqualTo(saved.id());
        assertThat(await(repo.list("client", new TaskFilter(0, 20, null, TaskFilter.SortField.CREATED_AT, TaskFilter.SortDirection.DESC))).total()).isEqualTo(1);
        pool.close().toCompletionStage().toCompletableFuture().join();
        vertx.close().toCompletionStage().toCompletableFuture().join();
    }

    private static <T> T await(Future<T> future) {
        return future.toCompletionStage().toCompletableFuture().join();
    }
}
