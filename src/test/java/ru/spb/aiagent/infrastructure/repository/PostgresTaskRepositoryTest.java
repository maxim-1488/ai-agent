package ru.spb.aiagent.infrastructure.repository;

import ru.spb.aiagent.domain.model.Task;
import ru.spb.aiagent.domain.model.TaskFilter;
import ru.spb.aiagent.domain.model.TaskStatus;
import ru.spb.aiagent.domain.exception.TaskConflictException;
import ru.spb.aiagent.domain.exception.TaskNotFoundException;
import ru.spb.aiagent.infrastructure.liquibase.LiquibaseMigrator;
import io.vertx.core.Vertx;
import io.vertx.core.Future;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class PostgresTaskRepositoryTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("ai_agent")
            .withUsername("ai_agent")
            .withPassword("ai_agent");

    @Test
    void createsAndListsTaskWithWhitelistedSort() {
        withRepository(repo -> {
            Task saved = await(repo.create(Task.create("client", "prompt", OffsetDateTime.now(ZoneOffset.UTC))));
            assertThat(await(repo.findByIdAndClientId(saved.id(), "client")).id()).isEqualTo(saved.id());
            assertThat(await(repo.list("client", new TaskFilter(0, 20, null, TaskFilter.SortField.CREATED_AT, TaskFilter.SortDirection.DESC))).total()).isEqualTo(1);
        });
    }

    @Test
    void cancelByDifferentClientReturnsNotFoundAndDoesNotAffectOwnerTask() {
        withRepository(repo -> {
            Task task = await(repo.create(Task.create("client-a", "prompt", OffsetDateTime.now(ZoneOffset.UTC))));
            Task inProgress = await(repo.markInProgress(task.id(), task.version()));

            assertThatThrownBy(() -> await(repo.cancel(task.id(), "client-b")))
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(TaskNotFoundException.class);

            Task visibleToOwner = await(repo.findByIdAndClientId(task.id(), "client-a"));
            assertThat(visibleToOwner.status()).isEqualTo(TaskStatus.IN_PROGRESS);
            Task completed = await(repo.complete(inProgress.id(), TaskStatus.COMPLETED, "result", null));
            assertThat(completed.status()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(completed.result()).isEqualTo("result");
        });
    }

    @Test
    void completeAfterSuccessfulCancelDoesNotChangeCancelledTask() {
        withRepository(repo -> {
            Task task = await(repo.create(Task.create("client", "prompt", OffsetDateTime.now(ZoneOffset.UTC))));
            Task inProgress = await(repo.markInProgress(task.id(), task.version()));
            Task cancelled = await(repo.cancel(inProgress.id(), "client"));

            assertThatThrownBy(() -> await(repo.complete(task.id(), TaskStatus.COMPLETED, "late result", null)))
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(TaskConflictException.class);

            Task saved = await(repo.findByIdAndClientId(task.id(), "client"));
            assertThat(saved.status()).isEqualTo(TaskStatus.CANCELLED);
            assertThat(saved.version()).isEqualTo(cancelled.version());
            assertThat(saved.result()).isNull();
        });
    }

    @Test
    void progressAfterSuccessfulCancelDoesNotChangeCancelledTask() {
        withRepository(repo -> {
            Task task = await(repo.create(Task.create("client", "prompt", OffsetDateTime.now(ZoneOffset.UTC))));
            Task inProgress = await(repo.markInProgress(task.id(), task.version()));
            Task progressed = await(repo.updateProgress(inProgress.id(), 40));
            Task cancelled = await(repo.cancel(progressed.id(), "client"));

            assertThatThrownBy(() -> await(repo.updateProgress(task.id(), 90)))
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(TaskConflictException.class);

            Task saved = await(repo.findByIdAndClientId(task.id(), "client"));
            assertThat(saved.status()).isEqualTo(TaskStatus.CANCELLED);
            assertThat(saved.progress()).isEqualTo(40);
            assertThat(saved.version()).isEqualTo(cancelled.version());
        });
    }

    @Test
    void repeatedCancelKeepsConflictSemantics() {
        withRepository(repo -> {
            Task task = await(repo.create(Task.create("client", "prompt", OffsetDateTime.now(ZoneOffset.UTC))));
            await(repo.cancel(task.id(), "client"));

            assertThatThrownBy(() -> await(repo.cancel(task.id(), "client")))
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(TaskConflictException.class);

            assertThat(await(repo.findByIdAndClientId(task.id(), "client")).status()).isEqualTo(TaskStatus.CANCELLED);
        });
    }

    @Test
    void concurrentCancelVsCompleteLeavesOnlyOneTerminalState() {
        withRepository(repo -> {
            Task task = await(repo.create(Task.create("client", "prompt", OffsetDateTime.now(ZoneOffset.UTC))));
            await(repo.markInProgress(task.id(), task.version()));

            Future<Task> cancel = repo.cancel(task.id(), "client");
            Future<Task> complete = repo.complete(task.id(), TaskStatus.COMPLETED, "result", null);
            Future.all(cancel.recover(error -> Future.succeededFuture()), complete.recover(error -> Future.succeededFuture()))
                    .toCompletionStage().toCompletableFuture().join();

            Task saved = await(repo.findByIdAndClientId(task.id(), "client"));
            assertThat(saved.status()).isIn(TaskStatus.CANCELLED, TaskStatus.COMPLETED);
            if (cancel.succeeded()) {
                assertThat(complete.failed()).isTrue();
                assertThat(saved.status()).isEqualTo(TaskStatus.CANCELLED);
                assertThat(saved.result()).isNull();
            }
            if (complete.succeeded()) {
                assertThat(cancel.failed()).isTrue();
                assertThat(saved.status()).isEqualTo(TaskStatus.COMPLETED);
                assertThat(saved.result()).isEqualTo("result");
            }
        });
    }

    private static <T> T await(Future<T> future) {
        return future.toCompletionStage().toCompletableFuture().join();
    }

    private static void withRepository(RepositoryAssertion assertion) {
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
                .with(new PoolOptions().setMaxSize(4))
                .build();
        try {
            await(pool.preparedQuery("DELETE FROM task_event").execute());
            await(pool.preparedQuery("DELETE FROM ai_task").execute());
            assertion.run(new PostgresTaskRepository(pool));
        } finally {
            pool.close().toCompletionStage().toCompletableFuture().join();
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }

    @FunctionalInterface
    private interface RepositoryAssertion {
        void run(PostgresTaskRepository repository);
    }
}
