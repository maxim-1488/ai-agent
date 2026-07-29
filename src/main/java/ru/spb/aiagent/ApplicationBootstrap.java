package ru.spb.aiagent;

import ru.spb.aiagent.infrastructure.config.AppConfig;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap приложения: создаёт Vert.x, deploy основного verticle и регистрирует graceful shutdown.
 */
public class ApplicationBootstrap {
    private static final Logger log = LoggerFactory.getLogger(ApplicationBootstrap.class);
    private static final long STARTUP_TIMEOUT_SECONDS = 60;

    private final VertxFactory vertxFactory;

    /**
     * Создаёт bootstrap с production-фабрикой Vert.x.
     */
    public ApplicationBootstrap() {
        this(new VertxFactory());
    }

    ApplicationBootstrap(VertxFactory vertxFactory) {
        this.vertxFactory = vertxFactory;
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
     * @return Future runtime приложения, готового принимать HTTP-запросы
     */
    public Future<ApplicationRuntime> startAsync(AppConfig config) {
        Vertx vertx = vertxFactory.create();
        Promise<ApplicationRuntime> startup = Promise.promise();
        vertx.deployVerticle(new MainVerticle(config))
                .onSuccess(deploymentId -> {
                    ShutdownManager shutdownManager = new ShutdownManager(vertx);
                    shutdownManager.register();
                    startup.complete(new ApplicationRuntime(vertx, deploymentId, shutdownManager));
                })
                .onFailure(startupError -> closeVertxAfterStartupFailure(vertx, startupError, startup));
        return startup.future();
    }

    /**
     * Запускает приложение и падает fail-fast, если асинхронный startup не дошёл до работающего HTTP server.
     *
     * @param config валидированная конфигурация приложения
     */
    public void start(AppConfig config) {
        try {
            startAsync(config).toCompletionStage()
                    .toCompletableFuture()
                    .get(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Application startup was interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Application startup failed", e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("Application startup timed out", e);
        }
    }

    private void closeVertxAfterStartupFailure(
            Vertx vertx,
            Throwable startupError,
            Promise<ApplicationRuntime> startup) {
        vertx.close()
                .onComplete(closeResult -> {
                    if (closeResult.failed()) {
                        Throwable closeError = closeResult.cause();
                        startupError.addSuppressed(closeError);
                        log.warn("Failed to close Vert.x after startup failure", closeError);
                    }
                    startup.fail(startupError);
                });
    }
}
