package ru.spb.aiagent;

import io.vertx.core.Vertx;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Graceful shutdown manager: регистрирует JVM hook и закрывает Vert.x.
 *
 * <p>При закрытии Vert.x платформа undeploy всех verticle. Поэтому ресурсы приложения
 * освобождаются в {@link MainVerticle#stop(io.vertx.core.Promise)}, а этот класс остаётся
 * только адаптером между JVM shutdown hook и lifecycle Vert.x.
 */
public class ShutdownManager {
    private static final Logger log = LoggerFactory.getLogger(ShutdownManager.class);

    private final Vertx vertx;

    /**
     * Создаёт manager shutdown.
     *
     * @param vertx Vert.x instance приложения
     */
    public ShutdownManager(Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * Регистрирует JVM shutdown hook.
     */
    public void register() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    /**
     * Выполняет graceful shutdown через закрытие Vert.x.
     */
    public void shutdown() {
        CountDownLatch shutdownCompleted = new CountDownLatch(1);
        log.info("Graceful shutdown initiated");
        vertx.close()
                .onSuccess(done -> log.info("Graceful shutdown completed"))
                .onFailure(error -> log.error("Failed to stop Vert.x during graceful shutdown", error))
                .onComplete(done -> shutdownCompleted.countDown());
        awaitShutdown(shutdownCompleted);
    }

    private void awaitShutdown(CountDownLatch shutdownCompleted) {
        try {
            if (!shutdownCompleted.await(10, TimeUnit.SECONDS)) {
                log.warn("Graceful shutdown did not complete within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Graceful shutdown wait was interrupted");
        }
    }
}
