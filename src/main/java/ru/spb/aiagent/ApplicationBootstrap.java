package ru.spb.aiagent;

import ru.spb.aiagent.application.service.InMemoryTaskExecutionRegistry;
import ru.spb.aiagent.application.service.SystemClockProvider;
import ru.spb.aiagent.application.usecase.CancelTaskUseCase;
import ru.spb.aiagent.application.usecase.CreateTaskUseCase;
import ru.spb.aiagent.application.usecase.ExecuteTaskUseCase;
import ru.spb.aiagent.application.usecase.GetTaskUseCase;
import ru.spb.aiagent.application.usecase.ListTasksUseCase;
import ru.spb.aiagent.infrastructure.ai.MockAiClient;
import ru.spb.aiagent.infrastructure.ai.MockAiOptions;
import ru.spb.aiagent.infrastructure.config.AppConfig;
import ru.spb.aiagent.infrastructure.db.PgPoolFactory;
import ru.spb.aiagent.infrastructure.liquibase.LiquibaseMigrator;
import ru.spb.aiagent.infrastructure.repository.PostgresTaskRepository;
import ru.spb.aiagent.web.rest.RouterFactory;
import ru.spb.aiagent.web.rest.TaskRestHandler;
import ru.spb.aiagent.web.websocket.HeartbeatService;
import ru.spb.aiagent.web.websocket.TaskWebSocketHandler;
import ru.spb.aiagent.web.websocket.WebSocketSubscriptionRegistry;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.sqlclient.Pool;

/**
 * Bootstrap приложения: config → Liquibase → PgPool → use cases → routes → HTTP server.
 */
public class ApplicationBootstrap {
    /**
     * Запускает приложение и падает fail-fast при ошибках миграций или bind HTTP-port.
     */
    public void start(AppConfig config) {
        new LiquibaseMigrator().migrate(config.jdbcUrl(), config.databaseUser(), config.databasePassword());
        Vertx vertx = new VertxFactory().create();
        Pool pool = new PgPoolFactory().create(vertx, config);
        PostgresTaskRepository repository = new PostgresTaskRepository(pool);
        WebSocketSubscriptionRegistry publisher = new WebSocketSubscriptionRegistry();
        InMemoryTaskExecutionRegistry executions = new InMemoryTaskExecutionRegistry();
        MockAiClient ai = new MockAiClient(vertx, new MockAiOptions(config.aiStepDelayMs(), config.aiTimeoutMs()));
        ExecuteTaskUseCase execute = new ExecuteTaskUseCase(repository, ai, executions, publisher);
        CreateTaskUseCase create = new CreateTaskUseCase(repository, new SystemClockProvider(), execute, publisher);
        GetTaskUseCase get = new GetTaskUseCase(repository);
        ListTasksUseCase list = new ListTasksUseCase(repository);
        CancelTaskUseCase cancel = new CancelTaskUseCase(repository, executions, publisher);
        TaskRestHandler rest = new TaskRestHandler(create, get, list, cancel);
        TaskWebSocketHandler ws = new TaskWebSocketHandler(get, publisher);
        Router router = new RouterFactory().create(vertx, rest, ws);
        HeartbeatService heartbeat = new HeartbeatService(vertx);
        heartbeat.start(() -> { });
        new ShutdownManager(vertx, pool, publisher, heartbeat).register();
        vertx.createHttpServer().requestHandler(router).listen(config.httpPort()).onFailure(error -> {
            throw new IllegalStateException("Failed to start HTTP server", error);
        });
    }
}
