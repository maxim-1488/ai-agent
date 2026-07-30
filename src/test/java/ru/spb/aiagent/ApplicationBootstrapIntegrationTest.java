package ru.spb.aiagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ru.spb.aiagent.infrastructure.config.AppConfig;
import ru.spb.aiagent.infrastructure.config.DatabaseConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class ApplicationBootstrapIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("ai_agent")
            .withUsername("ai_agent")
            .withPassword("ai_agent");

    @Test
    void startAsyncFailsAndClosesVertxWhenHttpPortIsOccupied() throws Exception {
        try (ServerSocket occupiedPort = new ServerSocket(0)) {
            CapturingVertxFactory vertxFactory = new CapturingVertxFactory();
            ApplicationBootstrap bootstrap = new ApplicationBootstrap(vertxFactory);
            AppConfig config = config(occupiedPort.getLocalPort());

            assertThatThrownBy(() -> await(bootstrap.startAsync(config)))
                    .isInstanceOf(CompletionException.class)
                    .satisfies(error -> assertThat(error.getCause()).isNotNull());

            assertThat(vertxFactory.vertx).isNotNull();
            assertVertxIsClosed(vertxFactory.vertx);
        }
    }

    @Test
    void startAsyncSucceedsAfterHttpServerListensAndRuntimeShutsDown() throws Exception {
        CapturingVertxFactory vertxFactory = new CapturingVertxFactory();
        ApplicationBootstrap bootstrap = new ApplicationBootstrap(vertxFactory);
        int port = freePort();
        await(bootstrap.startAsync(config(port)));

        try {
            HttpResponse<String> health = send("GET", port, "/health", null);
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).contains("\"status\":\"UP\"");

            HttpResponse<String> created = send("POST", port, "/api/v1/tasks", "{\"prompt\":\"test prompt\"}");
            assertThat(created.statusCode()).isEqualTo(201);
            assertThat(created.body()).contains("\"prompt\":\"test prompt\"");
        } finally {
            await(vertxFactory.vertx.close());
        }
    }

    @Test
    void startupRecoveryCompletesTaskInterruptedByRestartOnce() throws Exception {
        CapturingVertxFactory firstVertxFactory = new CapturingVertxFactory();
        ApplicationBootstrap firstBootstrap = new ApplicationBootstrap(firstVertxFactory);
        int firstPort = freePort();
        await(firstBootstrap.startAsync(config(firstPort, 100, 5000)));

        String taskId;
        try {
            HttpResponse<String> created = send("POST", firstPort, "/api/v1/tasks", "{\"prompt\":\"restart recovery prompt\"}");
            assertThat(created.statusCode()).isEqualTo(201);
            taskId = jsonString(created.body(), "id");
            awaitStatus(firstPort, taskId, "IN_PROGRESS");
        } finally {
            await(firstVertxFactory.vertx.close());
        }

        CapturingVertxFactory secondVertxFactory = new CapturingVertxFactory();
        ApplicationBootstrap secondBootstrap = new ApplicationBootstrap(secondVertxFactory);
        int secondPort = freePort();
        await(secondBootstrap.startAsync(config(secondPort, 5, 5000)));

        try {
            String completedBody = awaitStatus(secondPort, taskId, "COMPLETED");
            assertThat(completedBody).contains("\"status\":\"COMPLETED\"");
            assertThat(terminalEventCount(taskId)).isEqualTo(1);
        } finally {
            await(secondVertxFactory.vertx.close());
        }
    }

    private static HttpResponse<String> send(String method, int port, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .header("X-Client-Id", "bootstrap-test-client");
        if ("POST".equals(method)) {
            request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.GET();
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static AppConfig config(int httpPort) {
        return config(httpPort, 1, 1000);
    }

    private static AppConfig config(int httpPort, int aiStepDelayMs, int aiTimeoutMs) {
        return new AppConfig(
                httpPort,
                new DatabaseConfig(
                        postgres.getHost(),
                        postgres.getFirstMappedPort(),
                        postgres.getDatabaseName(),
                        postgres.getUsername(),
                        postgres.getPassword(),
                        4,
                        postgres.getJdbcUrl()),
                aiStepDelayMs,
                aiTimeoutMs,
                8192);
    }

    private static String awaitStatus(int port, String taskId, String expectedStatus) throws Exception {
        AssertionError lastError = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            HttpResponse<String> response = send("GET", port, "/api/v1/tasks/" + taskId, null);
            try {
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).contains("\"status\":\"" + expectedStatus + "\"");
                return response.body();
            } catch (AssertionError error) {
                lastError = error;
                Thread.sleep(50);
            }
        }
        throw lastError == null ? new AssertionError("Task did not reach " + expectedStatus) : lastError;
    }

    private static String jsonString(String body, String field) {
        var matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        if (!matcher.find()) {
            throw new AssertionError("Missing JSON field: " + field);
        }
        return matcher.group(1);
    }

    private static int terminalEventCount(String taskId) throws Exception {
        try (var conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = conn.prepareStatement("SELECT count(*) FROM task_event WHERE task_id=?::uuid "
                     + "AND type IN ('TASK_COMPLETED','TASK_FAILED','TASK_TIMED_OUT','TASK_CANCELLED')")) {
            statement.setString(1, taskId);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new CompletionException(e.getCause());
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    private static void assertVertxIsClosed(Vertx vertx) {
        assertThatThrownBy(() -> vertx.createHttpServer().listen(0)
                .toCompletionStage()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS))
                .isInstanceOf(RuntimeException.class);
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
