package ru.spb.aiagent.infrastructure.ai;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class MockAiClientTest {
    private final Vertx vertx = Vertx.vertx();

    @AfterEach
    void close() {
        vertx.close();
    }

    @Test
    void returnsProgressAndResult(VertxTestContext ctx) {
        MockAiClient client = new MockAiClient(vertx, new MockAiOptions(1, 1000));
        client.run("id", "hello", progress -> io.vertx.core.Future.succeededFuture(), () -> false)
                .onComplete(ctx.succeeding(result -> ctx.verify(() -> ctx.completeNow())));
    }

    @Test
    void failsOnPromptKeyword(VertxTestContext ctx) {
        MockAiClient client = new MockAiClient(vertx, new MockAiOptions(1, 1000));
        client.run("id", "fail", progress -> io.vertx.core.Future.succeededFuture(), () -> false)
                .onComplete(ctx.failing(error -> ctx.completeNow()));
    }
}
