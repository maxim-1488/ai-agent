package ru.spb.aiagent.web.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ru.spb.aiagent.application.core.TaskRepository;
import ru.spb.aiagent.application.usecase.GetTaskUseCase;
import ru.spb.aiagent.domain.model.Task;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

@ExtendWith(VertxExtension.class)
class TaskWebSocketMessageSizeIntegrationTest {
    private static final int LIMIT_BYTES = 8192;

    @Test
    void vertxRejectsOversizedMessageBeforeJacksonAndServerKeepsServingOtherClients(Vertx vertx) throws Exception {
        TaskRepository repository = Mockito.mock(TaskRepository.class);
        WebSocketMessageParser parser = spy(new WebSocketMessageParser());
        WebSocketSubscriptionRegistry registry = new WebSocketSubscriptionRegistry();
        TaskWebSocketHandler handler = new TaskWebSocketHandler(new GetTaskUseCase(repository), registry, parser, LIMIT_BYTES);
        HttpServer server = startServer(vertx, handler);
        try {
            ConnectedWebSocket clientAConnection = connect(vertx, server.actualPort(), "client-a");
            WebSocket clientA = clientAConnection.socket();
            CountDownLatch clientAClosed = new CountDownLatch(1);
            clientA.closeHandler(v -> clientAClosed.countDown());
            String oversized = pingMessageWithByteSize(LIMIT_BYTES + 1);

            clientA.writeTextMessage(oversized);

            assertThat(clientAClosed.await(5, TimeUnit.SECONDS)).isTrue();
            verify(parser, never()).parse(eq(oversized));
            verify(repository, never()).findByIdAndClientId(any(), anyString());
            assertThat(registry.containsConnection(Mockito.mock(io.vertx.core.http.ServerWebSocket.class))).isFalse();

            UUID taskId = UUID.randomUUID();
            Task task = TestTaskCreator.task(taskId, "client-b");
            when(repository.findByIdAndClientId(taskId, "client-b")).thenReturn(io.vertx.core.Future.succeededFuture(task));
            ConnectedWebSocket clientBConnection = connect(vertx, server.actualPort(), "client-b");
            WebSocket clientB = clientBConnection.socket();
            CountDownLatch subscribed = new CountDownLatch(1);
            clientB.textMessageHandler(message -> {
                if (message.contains("\"type\":\"SUBSCRIBED\"") && message.contains(taskId.toString())) {
                    subscribed.countDown();
                }
            });

            clientB.writeTextMessage("{\"action\":\"SUBSCRIBE\",\"taskId\":\"" + taskId + "\"}");

            assertThat(subscribed.await(5, TimeUnit.SECONDS)).isTrue();
            clientB.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            clientAConnection.client().close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            clientBConnection.client().close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        } finally {
            server.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void vertxRejectsVeryLargeMessageBeforeJackson(Vertx vertx) throws Exception {
        TaskRepository repository = Mockito.mock(TaskRepository.class);
        WebSocketMessageParser parser = spy(new WebSocketMessageParser());
        WebSocketSubscriptionRegistry registry = new WebSocketSubscriptionRegistry();
        TaskWebSocketHandler handler = new TaskWebSocketHandler(new GetTaskUseCase(repository), registry, parser, LIMIT_BYTES);
        HttpServer server = startServer(vertx, handler);
        try {
            ConnectedWebSocket connection = connect(vertx, server.actualPort(), "client-large");
            WebSocket client = connection.socket();
            CountDownLatch closed = new CountDownLatch(1);
            client.closeHandler(v -> closed.countDown());
            String veryLarge = pingMessageWithByteSize(16_384);

            client.writeTextMessage(veryLarge);

            assertThat(closed.await(5, TimeUnit.SECONDS)).isTrue();
            verify(parser, never()).parse(eq(veryLarge));
            verify(repository, never()).findByIdAndClientId(any(), anyString());
            connection.client().close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        } finally {
            server.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    private static HttpServer startServer(Vertx vertx, TaskWebSocketHandler handler) throws Exception {
        Router router = Router.router(vertx);
        router.get("/ws/tasks").handler(handler);
        HttpServerOptions options = new HttpServerOptions()
                .setMaxWebSocketFrameSize(LIMIT_BYTES)
                .setMaxWebSocketMessageSize(LIMIT_BYTES);
        return vertx.createHttpServer(options)
                .requestHandler(router)
                .listen(0)
                .toCompletionStage()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
    }

    private static ConnectedWebSocket connect(Vertx vertx, int port, String clientId) throws Exception {
        WebSocketClient client = vertx.createWebSocketClient();
        WebSocket socket = client.connect(new WebSocketConnectOptions()
                        .setHost("localhost")
                        .setPort(port)
                        .setURI("/ws/tasks")
                        .putHeader("X-Client-Id", clientId))
                .toCompletionStage()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        return new ConnectedWebSocket(client, socket);
    }

    private static String pingMessageWithByteSize(int sizeBytes) {
        String prefix = "{\"action\":\"PING\",\"padding\":\"";
        String suffix = "\"}";
        return prefix + "x".repeat(sizeBytes - prefix.length() - suffix.length()) + suffix;
    }

    private record ConnectedWebSocket(WebSocketClient client, WebSocket socket) {
    }
}
