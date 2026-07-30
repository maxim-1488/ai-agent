package ru.spb.aiagent;

import ru.spb.aiagent.infrastructure.config.AppConfig;
import io.vertx.core.Verticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap приложения: создаёт Vert.x, deploy основного verticle и регистрирует graceful shutdown.
 */
public class ApplicationBootstrap {
    private static final Logger log = LoggerFactory.getLogger(ApplicationBootstrap.class);
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);

    private final VertxFactory vertxFactory;
    private final Duration startupTimeout;
    private final Function<AppConfig, Verticle> verticleFactory;

    /**
     * Создаёт bootstrap с production-фабрикой Vert.x.
     */
    public ApplicationBootstrap() {
        this(new VertxFactory(), STARTUP_TIMEOUT, MainVerticle::new);
    }

    ApplicationBootstrap(VertxFactory vertxFactory) {
        this(vertxFactory, STARTUP_TIMEOUT, MainVerticle::new);
    }

    ApplicationBootstrap(
            VertxFactory vertxFactory,
            Duration startupTimeout,
            Function<AppConfig, Verticle> verticleFactory) {
        this.vertxFactory = vertxFactory;
        this.startupTimeout = startupTimeout;
        this.verticleFactory = verticleFactory;
    }

    /**
     * Асинхронно запускает приложение.
     *
     * <p>Future завершается успешно только после успешного deploy основного verticle, то есть после выполнения
     * Liquibase-миграций, создания PostgreSQL pool, сборки HTTP/WebSocket инфраструктуры и успешного
     * {@code HttpServer.listen(...)}. При любой ошибке startup уже созданные ресурсы verticle закрываются его
     * lifecycle-кодом, затем bootstrap закрывает созданный им Vert.x instance и возвращает исходную ошибку startup.
     *
     * @param config валидированная конфигурация приложения
     * @return Future, который завершается после готовности приложения принимать HTTP-запросы
     */
    public Future<Void> startAsync(AppConfig config) {
        return createStartupLifecycle(config).startup().future();
    }

    private StartupLifecycle createStartupLifecycle(AppConfig config) {
        Vertx vertx = vertxFactory.create();
        Promise<Void> startup = Promise.promise();
        AtomicBoolean startupTerminated = new AtomicBoolean(false);
        AtomicBoolean closeStarted = new AtomicBoolean(false);
        vertx.deployVerticle(verticleFactory.apply(config))
                .onSuccess(deploymentId -> {
                    if (startupTerminated.compareAndSet(false, true)) {
                        ShutdownManager shutdownManager = new ShutdownManager(vertx);
                        shutdownManager.register();
                        startup.complete();
                    } else {
                        closeVertxOnce(vertx, closeStarted, null, "after startup timeout");
                    }
                })
                .onFailure(startupError -> {
                    if (startupTerminated.compareAndSet(false, true)) {
                        closeVertxAfterStartupFailure(vertx, startupError, startup, closeStarted);
                    }
                });
        return new StartupLifecycle(vertx, startup, startupTerminated, closeStarted);
    }

    /**
     * Запускает приложение и падает fail-fast, если асинхронный startup не дошёл до работающего HTTP server.
     *
     * @param config валидированная конфигурация приложения
     */
    public void start(AppConfig config) {
        StartupLifecycle startup = createStartupLifecycle(config);
        try {
            startup.startup().future().toCompletionStage()
                    .toCompletableFuture()
                    .get(startupTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeVertxAfterStartupTimeout(startup, e);
            throw new IllegalStateException("Application startup was interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Application startup failed", e.getCause());
        } catch (TimeoutException e) {
            closeVertxAfterStartupTimeout(startup, e);
            throw new IllegalStateException("Application startup timed out", e);
        }
    }

    private void closeVertxAfterStartupTimeout(StartupLifecycle startup, Throwable startupError) {
        if (startup.startupTerminated.compareAndSet(false, true)) {
            startup.startup().tryFail(startupError);
        }
        closeVertxOnce(startup.vertx(), startup.closeStarted(), startupError, "after startup timeout");
    }

    private void closeVertxAfterStartupFailure(
            Vertx vertx,
            Throwable startupError,
            Promise<Void> startup,
            AtomicBoolean closeStarted) {
        closeVertxOnce(vertx, closeStarted, startupError, "after startup failure")
                .onComplete(closeResult -> {
                    startup.tryFail(startupError);
                });
    }

    private Future<Void> closeVertxOnce(
            Vertx vertx,
            AtomicBoolean closeStarted,
            Throwable startupError,
            String reason) {
        if (!closeStarted.compareAndSet(false, true)) {
            return Future.succeededFuture();
        }
        return vertx.close()
                .onFailure(closeError -> {
                    if (startupError != null) {
                        startupError.addSuppressed(closeError);
                    }
                    log.warn("Failed to close Vert.x {}", reason, closeError);
                });
    }

    private record StartupLifecycle(
            Vertx vertx,
            Promise<Void> startup,
            AtomicBoolean startupTerminated,
            AtomicBoolean closeStarted) {
    }
}
