package ru.spb.aiagent;

import io.vertx.core.Vertx;

/**
 * Runtime успешно запущенного приложения.
 *
 * @param vertx Vert.x instance, созданный bootstrap-кодом
 * @param deploymentId идентификатор deploy основного verticle
 * @param shutdownManager зарегистрированный adapter graceful shutdown
 */
public record ApplicationRuntime(Vertx vertx, String deploymentId, ShutdownManager shutdownManager) {
}
