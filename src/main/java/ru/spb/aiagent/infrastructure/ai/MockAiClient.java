package ru.spb.aiagent.infrastructure.ai;

import ru.spb.aiagent.application.core.AiClient;
import ru.spb.aiagent.application.core.AiTimeoutException;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mock AI-адаптер с progress, ошибкой, timeout и отменой без Thread.sleep.
 *
 * <p>Слова {@code fail}, {@code error} в prompt приводят к FAILED, слово {@code timeout} —
 * к TIMED_OUT. Между шагами проверяется cancellation token.
 */
public class MockAiClient implements AiClient {
    private static final Logger log = LoggerFactory.getLogger(MockAiClient.class);

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
        log.info("Mock AI execution started: taskId={}, promptLength={}", taskId, prompt == null ? 0 : prompt.length());
        Promise<String> promise = Promise.promise();
        long timeoutTimer = vertx.setTimer(options.timeoutMs(), id -> {
            if (!promise.future().isComplete()) {
                log.warn("Mock AI execution timeout: taskId={}, timeoutMs={}", taskId, options.timeoutMs());
                promise.fail(new AiTimeoutException("AI execution exceeded timeout"));
            }
        });
        step(taskId, prompt, progressCallback, cancelToken, promise, 10, timeoutTimer);
        return promise.future();
    }

    private void step(
            String taskId,
            String prompt,
            ProgressCallback progressCallback,
            CancellationToken cancelToken,
            Promise<String> promise,
            int progress,
            long timeoutTimer) {
        if (promise.future().isComplete()) {
            return;
        }
        if (cancelToken.cancelled()) {
            vertx.cancelTimer(timeoutTimer);
            log.info("Mock AI execution cancelled: taskId={}", taskId);
            promise.fail(new AiExecutionException("AI execution was cancelled"));
            return;
        }
        vertx.setTimer(options.stepDelayMs(), id -> {
            if (prompt.toLowerCase().contains("timeout")) {
                log.debug("Mock AI timeout scenario keeps execution pending: taskId={}", taskId);
                return;
            }
            progressCallback.onProgress(progress).onComplete(ar -> {
                if (ar.failed()) {
                    if (cancelToken.cancelled()) {
                        log.debug("Mock AI progress callback stopped after cancellation: taskId={}, progress={}, reason={}",
                                taskId, progress, ar.cause().getMessage());
                        promise.fail(new AiExecutionException("AI execution was cancelled"));
                        return;
                    }
                    log.error("Mock AI progress callback failed: taskId={}, progress={}", taskId, progress, ar.cause());
                    promise.fail(ar.cause());
                    return;
                }
                log.debug("Mock AI execution step completed: taskId={}, progress={}", taskId, progress);
                if (prompt.toLowerCase().contains("fail") || prompt.toLowerCase().contains("error")) {
                    vertx.cancelTimer(timeoutTimer);
                    log.info("Mock AI execution failed by scenario: taskId={}", taskId);
                    promise.fail(new AiExecutionException("Mock AI returned an error"));
                } else if (progress >= 100) {
                    vertx.cancelTimer(timeoutTimer);
                    log.info("Mock AI execution completed: taskId={}", taskId);
                    promise.complete("Mock AI result: " + prompt);
                } else {
                    step(taskId, prompt, progressCallback, cancelToken, promise, progress + 10, timeoutTimer);
                }
            });
        });
    }
}
