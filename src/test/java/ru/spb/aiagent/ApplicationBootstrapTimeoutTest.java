package ru.spb.aiagent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ru.spb.aiagent.infrastructure.config.AppConfig;
import ru.spb.aiagent.infrastructure.config.DatabaseConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ApplicationBootstrapTimeoutTest {

    @Test
    void startClosesVertxOnStartupTimeoutAndPreventsLateHttpServerStart() throws Exception {
        CapturingVertxFactory vertxFactory = new CapturingVertxFactory();
        int port = freePort();
        ApplicationBootstrap bootstrap = new ApplicationBootstrap(
                vertxFactory,
                Duration.ofMillis(50),
                ignored -> new NeverCompletingDelayedHttpServerVerticle(port));

        assertThatThrownBy(() -> bootstrap.start(config(port)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Application startup timed out");

        assertVertxCloses(vertxFactory.vertx);
        assertNoLateHttpServerStarts(port);
    }

    private static AppConfig config(int httpPort) {
        return new AppConfig(
                httpPort,
                new DatabaseConfig(
                        "localhost",
                        5432,
                        "ai_agent",
                        "ai_agent",
                        "ai_agent",
                        4,
                        "jdbc:postgresql://localhost:5432/ai_agent"),
                1,
                1000,
                8192);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void assertVertxCloses(Vertx vertx) throws InterruptedException {
        AssertionError lastError = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            try {
                assertThatThrownBy(() -> vertx.createHttpServer().listen(0)
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get(1, TimeUnit.SECONDS))
                        .isInstanceOf(RuntimeException.class);
                return;
            } catch (AssertionError error) {
                lastError = error;
                Thread.sleep(50);
            }
        }
        throw lastError == null ? new AssertionError("Vert.x did not close") : lastError;
    }

    private static void assertNoLateHttpServerStarts(int port) throws Exception {
        Thread.sleep(500);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/"))
                .timeout(Duration.ofMillis(300))
                .GET()
                .build();

        assertThatThrownBy(() -> HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()))
                .isInstanceOf(IOException.class);
    }

    private static final class NeverCompletingDelayedHttpServerVerticle extends AbstractVerticle {
        private final int port;

        private NeverCompletingDelayedHttpServerVerticle(int port) {
            this.port = port;
        }

        @Override
        public void start(Promise<Void> startPromise) {
            vertx.setTimer(250, ignored -> vertx.createHttpServer()
                    .requestHandler(request -> request.response().end("late"))
                    .listen(port));
        }
    }

    private static final class CapturingVertxFactory extends VertxFactory {
        private Vertx vertx;

        @Override
        public Vertx create() {
            vertx = super.create();
            return vertx;
        }
    }
}
