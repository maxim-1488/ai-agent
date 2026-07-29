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
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.sqlclient.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Основной Vert.x verticle, владеющий жизненным циклом backend-инфраструктуры.
 *
 * <p>Verticle запускает миграции, создаёт PostgreSQL pool, собирает application use cases,
 * подключает REST/WebSocket маршруты и открывает HTTP server. При остановке он закрывает
 * ресурсы в обратном порядке, чтобы entry point приложения не содержал прикладную сборку
 * и ручную cleanup-логику.
 */
public final class MainVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

    private final AppConfig config;
    private HttpServer httpServer;
    private Pool pool;
    private WebSocketSubscriptionRegistry publisher;
    private HeartbeatService heartbeat;

    /**
     * Создаёт основной verticle приложения.
     *
     * @param config валидированная конфигурация приложения
     */
    public MainVerticle(AppConfig config) {
        this.config = config;
    }

    /**
     * Асинхронно запускает инфраструктуру приложения.
     *
     * @param startPromise promise Vert.x, отражающий результат запуска verticle
     */
    @Override
    public void start(Promise<Void> startPromise) {
        logStartupConfiguration();
        runMigrations()
                .compose(ignored -> createInfrastructure())
                .compose(ignored -> startHttpServer())
                .compose(ignored -> startHeartbeat())
                .onSuccess(ignored -> startPromise.complete())
                .onFailure(error -> {
                    log.error("Application infrastructure startup failed", error);
                    closeAfterStartupFailure(error)
                            .onComplete(closeResult -> startPromise.fail(error));
                });
    }

    private void logStartupConfiguration() {
        var database = config.database();
        log.info("Application startup initiated");
        log.info("Configuration loaded: httpPort={}, databaseHost={}, databasePort={}, databaseName={}, "
                        + "databasePoolSize={}, aiStepDelayMs={}, aiTimeoutMs={}, websocketMaxMessageSizeBytes={}",
                config.httpPort(), database.host(), database.port(), database.name(),
                database.poolSize(), config.aiStepDelayMs(), config.aiTimeoutMs(),
                config.websocketMaxMessageSizeBytes());
    }

    private Future<Void> runMigrations() {
        log.info("Starting Liquibase migrations");
        return vertx.executeBlocking(() -> {
                    var database = config.database();
                    new LiquibaseMigrator().migrate(database.jdbcUrl(), database.user(), database.password());
                    return Boolean.TRUE;
                })
                .onSuccess(ignored -> log.info("Liquibase migrations completed successfully"))
                .mapEmpty();
    }

    private Future<Void> createInfrastructure() {
        var database = config.database();
        log.info("Creating PostgreSQL pool: host={}, port={}, database={}, poolSize={}",
                database.host(), database.port(), database.name(), database.poolSize());
        pool = new PgPoolFactory().create(vertx, database);
        publisher = new WebSocketSubscriptionRegistry();
        heartbeat = new HeartbeatService(vertx);
        return Future.succeededFuture();
    }

    private Future<Void> startHttpServer() {
        PostgresTaskRepository repository = new PostgresTaskRepository(pool);
        InMemoryTaskExecutionRegistry executions = new InMemoryTaskExecutionRegistry();
        MockAiClient ai = new MockAiClient(vertx, new MockAiOptions(config.aiStepDelayMs(), config.aiTimeoutMs()));
        ExecuteTaskUseCase execute = new ExecuteTaskUseCase(repository, ai, executions, publisher);
        CreateTaskUseCase create = new CreateTaskUseCase(repository, new SystemClockProvider(), execute, publisher);
        GetTaskUseCase get = new GetTaskUseCase(repository);
        ListTasksUseCase list = new ListTasksUseCase(repository);
        CancelTaskUseCase cancel = new CancelTaskUseCase(repository, executions, publisher);
        TaskRestHandler rest = new TaskRestHandler(create, get, list, cancel);
        TaskWebSocketHandler ws = new TaskWebSocketHandler(get, publisher, config.websocketMaxMessageSizeBytes());
        var router = new RouterFactory().create(vertx, rest, ws);
        HttpServerOptions options = new HttpServerOptions()
                .setMaxWebSocketFrameSize(config.websocketMaxMessageSizeBytes())
                .setMaxWebSocketMessageSize(config.websocketMaxMessageSizeBytes());

        log.info("Starting HTTP server: requestedPort={}, websocketMaxMessageSizeBytes={}",
                config.httpPort(), config.websocketMaxMessageSizeBytes());
        httpServer = vertx.createHttpServer(options);
        return httpServer.requestHandler(router)
                .listen(config.httpPort())
                .onSuccess(server -> log.info("HTTP server started: actualPort={}; application is ready to accept requests",
                        server.actualPort()))
                .mapEmpty();
    }

    private Future<Void> startHeartbeat() {
        heartbeat.start(() -> { });
        log.info("Heartbeat started");
        log.info("Application startup completed");
        return Future.succeededFuture();
    }

    /**
     * Останавливает ресурсы, которыми владеет verticle.
     *
     * @param stopPromise promise Vert.x, отражающий результат остановки verticle
     */
    @Override
    public void stop(Promise<Void> stopPromise) {
        closeHttpServer()
                .compose(ignored -> stopHeartbeat())
                .compose(ignored -> closeWebSockets())
                .compose(ignored -> closePool())
                .onSuccess(ignored -> stopPromise.complete())
                .onFailure(stopPromise::fail);
    }

    private Future<Void> closeHttpServer() {
        if (httpServer == null) {
            return Future.succeededFuture();
        }
        if (httpServer.actualPort() <= 0) {
            httpServer = null;
            return Future.succeededFuture();
        }
        return httpServer.close()
                .onSuccess(ignored -> log.info("HTTP server stopped"));
    }

    private Future<Void> stopHeartbeat() {
        if (heartbeat != null) {
            heartbeat.stop();
            log.info("Heartbeat stopped");
        }
        return Future.succeededFuture();
    }

    private Future<Void> closeWebSockets() {
        if (publisher != null) {
            publisher.closeAll();
        }
        return Future.succeededFuture();
    }

    private Future<Void> closePool() {
        if (pool == null) {
            return Future.succeededFuture();
        }
        return pool.close()
                .onSuccess(ignored -> log.info("PostgreSQL pool closed"));
    }

    private Future<Void> closeAfterStartupFailure(Throwable startupError) {
        return cleanupAfterStartupFailure("HTTP server", this::closeHttpServer, startupError)
                .compose(ignored -> cleanupAfterStartupFailure("heartbeat", this::stopHeartbeat, startupError))
                .compose(ignored -> cleanupAfterStartupFailure("WebSocket connections", this::closeWebSockets, startupError))
                .compose(ignored -> cleanupAfterStartupFailure("PostgreSQL pool", this::closePool, startupError));
    }

    private Future<Void> cleanupAfterStartupFailure(String resourceName, CleanupAction cleanup, Throwable startupError) {
        return cleanup.close()
                .recover(cleanupError -> {
                    startupError.addSuppressed(cleanupError);
                    log.warn("Failed to close {} after startup failure", resourceName, cleanupError);
                    return Future.succeededFuture();
                });
    }

    @FunctionalInterface
    private interface CleanupAction {
        Future<Void> close();
    }

    /**
     * Возвращает фактический порт HTTP-сервера после запуска.
     *
     * @return фактический порт HTTP-сервера или {@code -1}, если сервер не запущен
     */
    public int actualPort() {
        return httpServer == null ? -1 : httpServer.actualPort();
    }
}
