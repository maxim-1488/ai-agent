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
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.sqlclient.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap приложения: config → Liquibase → PgPool → use cases → routes → HTTP server.
 */
public class ApplicationBootstrap {
    private static final Logger log = LoggerFactory.getLogger(ApplicationBootstrap.class);

    /**
     * Запускает приложение и падает fail-fast при ошибках миграций или bind HTTP-port.
     */
    public void start(AppConfig config) {
        log.info("Application startup initiated");
        log.info("Configuration loaded: httpPort={}, databaseHost={}, databasePort={}, databaseName={}, databasePoolSize={}, aiStepDelayMs={}, aiTimeoutMs={}, websocketMaxMessageSizeBytes={}",
                config.httpPort(), config.databaseHost(), config.databasePort(), config.databaseName(),
                config.databasePoolSize(), config.aiStepDelayMs(), config.aiTimeoutMs(), config.websocketMaxMessageSizeBytes());
        log.info("Starting Liquibase migrations");
        new LiquibaseMigrator().migrate(config.jdbcUrl(), config.databaseUser(), config.databasePassword());
        log.info("Liquibase migrations completed successfully");
        Vertx vertx = new VertxFactory().create();
        log.info("Creating PostgreSQL pool: host={}, port={}, database={}, poolSize={}",
                config.databaseHost(), config.databasePort(), config.databaseName(), config.databasePoolSize());
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
        TaskWebSocketHandler ws = new TaskWebSocketHandler(get, publisher, config.websocketMaxMessageSizeBytes());
        Router router = new RouterFactory().create(vertx, rest, ws);
        HeartbeatService heartbeat = new HeartbeatService(vertx);
        heartbeat.start(() -> { });
        new ShutdownManager(vertx, pool, publisher, heartbeat).register();
        HttpServerOptions httpServerOptions = new HttpServerOptions()
                .setMaxWebSocketFrameSize(config.websocketMaxMessageSizeBytes())
                .setMaxWebSocketMessageSize(config.websocketMaxMessageSizeBytes());
        log.info("Starting HTTP server: requestedPort={}, websocketMaxMessageSizeBytes={}",
                config.httpPort(), config.websocketMaxMessageSizeBytes());
        vertx.createHttpServer(httpServerOptions).requestHandler(router).listen(config.httpPort())
                .onSuccess(server -> log.info("HTTP server started: actualPort={}; application is ready to accept requests", server.actualPort()))
                .onFailure(error -> {
                    log.error("Failed to start HTTP server: requestedPort={}", config.httpPort(), error);
                    throw new IllegalStateException("Failed to start HTTP server", error);
                });
    }
}
