package ru.spb.aiagent.infrastructure.ai;

import ru.spb.aiagent.application.core.AiClient;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Mock AI-адаптер с progress, ошибкой, timeout и отменой без Thread.sleep.
 *
 * <p>Слова {@code fail}, {@code error} в prompt приводят к FAILED, слово {@code timeout} —
 * к TIMED_OUT. Между шагами проверяется cancellation token.
 */
public class MockAiClient implements AiClient {
    private final Vertx vertx;
    private final MockAiOptions options;

    /**
     * Создаёт mock AI-адаптер.
     */
    public MockAiClient(Vertx vertx, MockAiOptions options) {
        this.vertx = vertx;
        this.options = options;
    }

    /**
     * Выполняет prompt через Vert.x timers и отправляет progress 10..100.
     */
    @Override
    public Future<String> run(String taskId, String prompt, ProgressCallback progressCallback, CancellationToken cancelToken) {
        Promise<String> promise = Promise.promise();
        long timeoutTimer = vertx.setTimer(options.timeoutMs(), id -> {
            if (!promise.future().isComplete()) {
                promise.fail(new AiTimeoutException("AI execution exceeded timeout"));
            }
        });
        step(prompt, progressCallback, cancelToken, promise, 10, timeoutTimer);
        return promise.future();
    }

    private void step(String prompt, ProgressCallback progressCallback, CancellationToken cancelToken, Promise<String> promise, int progress, long timeoutTimer) {
        if (promise.future().isComplete()) {
            return;
        }
        if (cancelToken.cancelled()) {
            vertx.cancelTimer(timeoutTimer);
            promise.fail(new AiExecutionException("AI execution was cancelled"));
            return;
        }
        vertx.setTimer(options.stepDelayMs(), id -> {
            if (prompt.toLowerCase().contains("timeout")) {
                return;
            }
            progressCallback.onProgress(progress).onComplete(ar -> {
                if (ar.failed()) {
                    promise.fail(ar.cause());
                    return;
                }
                if (prompt.toLowerCase().contains("fail") || prompt.toLowerCase().contains("error")) {
                    vertx.cancelTimer(timeoutTimer);
                    promise.fail(new AiExecutionException("Mock AI returned an error"));
                } else if (progress >= 100) {
                    vertx.cancelTimer(timeoutTimer);
                    promise.complete("Mock AI result: " + prompt);
                } else {
                    step(prompt, progressCallback, cancelToken, promise, progress + 10, timeoutTimer);
                }
            });
        });
    }
}
