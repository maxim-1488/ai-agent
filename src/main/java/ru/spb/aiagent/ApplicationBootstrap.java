package ru.spb.aiagent;

import ru.spb.aiagent.infrastructure.config.AppConfig;
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

    /**
     * Запускает приложение и падает fail-fast при ошибках deploy основного verticle.
     */
    public void start(AppConfig config) {
        Vertx vertx = new VertxFactory().create();
        try {
            vertx.deployVerticle(new MainVerticle(config))
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            new ShutdownManager(vertx).register();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeVertx(vertx);
            throw new IllegalStateException("Application startup was interrupted", e);
        } catch (ExecutionException e) {
            closeVertx(vertx);
            throw new IllegalStateException("Application startup failed", e.getCause());
        } catch (TimeoutException e) {
            closeVertx(vertx);
            throw new IllegalStateException("Application startup timed out", e);
        }
    }

    private void closeVertx(Vertx vertx) {
        vertx.close().onFailure(error -> log.warn("Failed to close Vert.x after startup failure", error));
    }
}
