package ru.spb.aiagent.application.core;

import io.vertx.core.Future;

/**
 * Порт AI-клиента; реализация может быть mock или реальным внешним API.
 */
public interface AiClient {
    /**
     * Выполняет prompt асинхронно, вызывает progressCallback значениями 0..100 и уважает cancelToken.
     */
    Future<String> run(String taskId, String prompt, ProgressCallback progressCallback, CancellationToken cancelToken);

    /**
     * Callback прогресса AI-выполнения.
     */
    interface ProgressCallback {
        /**
         * Принимает новое значение прогресса.
         */
        Future<Void> onProgress(int progress);
    }

    /**
     * Токен отмены, проверяемый AI-адаптером между шагами.
     */
    interface CancellationToken {
        /**
         * Возвращает true, если выполнение отменено.
         */
        boolean cancelled();
    }
}
