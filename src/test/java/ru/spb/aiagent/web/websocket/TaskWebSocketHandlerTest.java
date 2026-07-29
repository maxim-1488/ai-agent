package ru.spb.aiagent.web.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ru.spb.aiagent.application.core.TaskRepository;
import ru.spb.aiagent.application.usecase.GetTaskUseCase;
import ru.spb.aiagent.domain.model.Task;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.ServerWebSocket;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TaskWebSocketHandlerTest {

    private static final String CLIENT_ID = "client-1";

    private final TaskRepository repository = mock(TaskRepository.class);
    private final WebSocketSubscriptionRegistry registry = new WebSocketSubscriptionRegistry();
    private final TaskWebSocketHandler handler = new TaskWebSocketHandler(new GetTaskUseCase(repository), registry);

    @Test
    void lateSubscribeSuccessAfterCloseDoesNotRestoreConnectionOrSendMessages() {
        ServerWebSocket socket = openSocket();
        UUID taskId = UUID.randomUUID();
        Task task = TestTaskCreator.task(taskId, CLIENT_ID);
        Promise<Task> pendingGetTask = Promise.promise();
        when(repository.findByIdAndClientId(taskId, CLIENT_ID)).thenReturn(pendingGetTask.future());

        BoundSocket bound = bind(socket);
        bound.textHandler.handle(subscribeMessage(taskId));
        bound.closeHandler.handle(null);
        pendingGetTask.complete(task);

        assertThat(registry.containsConnection(socket)).isFalse();
        assertThat(registry.containsSocketSubscriptions(socket)).isFalse();
        assertThat(registry.hasSubscription(socket, taskId)).isFalse();
        assertThat(registry.subscriberCount(taskId)).isZero();
        verify(socket, never()).writeTextMessage(anyString());
    }

    @Test
    void normalSubscribeCreatesSubscriptionAndSendsCurrentTaskState() {
        ServerWebSocket socket = openSocket();
        UUID taskId = UUID.randomUUID();
        Task task = TestTaskCreator.task(taskId, CLIENT_ID);
        when(repository.findByIdAndClientId(taskId, CLIENT_ID)).thenReturn(io.vertx.core.Future.succeededFuture(task));

        BoundSocket bound = bind(socket);
        bound.textHandler.handle(subscribeMessage(taskId));

        assertThat(registry.containsConnection(socket)).isTrue();
        assertThat(registry.hasSubscription(socket, taskId)).isTrue();
        verify(socket).writeTextMessage(org.mockito.ArgumentMatchers.argThat(message ->
                message.contains("\"type\":\"SUBSCRIBED\"")
                        && message.contains(taskId.toString())
                        && message.contains("\"task\"")));
    }

    @Test
    void closeAfterCompletedSubscribeRemovesSubscription() {
        ServerWebSocket socket = openSocket();
        UUID taskId = UUID.randomUUID();
        Task task = TestTaskCreator.task(taskId, CLIENT_ID);
        when(repository.findByIdAndClientId(taskId, CLIENT_ID)).thenReturn(io.vertx.core.Future.succeededFuture(task));

        BoundSocket bound = bind(socket);
        bound.textHandler.handle(subscribeMessage(taskId));
        bound.closeHandler.handle(null);

        assertThat(registry.containsConnection(socket)).isFalse();
        assertThat(registry.containsSocketSubscriptions(socket)).isFalse();
        assertThat(registry.hasSubscription(socket, taskId)).isFalse();
        assertThat(registry.subscriberCount(taskId)).isZero();
    }

    @Test
    void lateSubscribeFailureAfterCloseDoesNotSendError() {
        ServerWebSocket socket = openSocket();
        UUID taskId = UUID.randomUUID();
        Promise<Task> pendingGetTask = Promise.promise();
        when(repository.findByIdAndClientId(taskId, CLIENT_ID)).thenReturn(pendingGetTask.future());

        BoundSocket bound = bind(socket);
        bound.textHandler.handle(subscribeMessage(taskId));
        bound.closeHandler.handle(null);
        pendingGetTask.fail(new IllegalArgumentException("not found"));

        verify(socket, never()).writeTextMessage(anyString());
    }

    @Test
    void subscribeFailureForActiveConnectionDoesNotCreateSubscription() {
        ServerWebSocket socket = openSocket();
        UUID taskId = UUID.randomUUID();
        when(repository.findByIdAndClientId(taskId, CLIENT_ID))
                .thenReturn(io.vertx.core.Future.failedFuture(new IllegalArgumentException("not found")));

        BoundSocket bound = bind(socket);
        bound.textHandler.handle(subscribeMessage(taskId));

        assertThat(registry.hasSubscription(socket, taskId)).isFalse();
        verify(socket).writeTextMessage(org.mockito.ArgumentMatchers.argThat(message ->
                message.contains("\"type\":\"ERROR\"") && message.contains("not found")));
    }

    @Test
    void handlerRejectsOversizedMessageBeforeParserAndUseCase() throws Exception {
        ServerWebSocket socket = openSocket();
        WebSocketMessageParser parser = mock(WebSocketMessageParser.class);
        TaskWebSocketHandler limitedHandler = new TaskWebSocketHandler(new GetTaskUseCase(repository), registry, parser, 32);

        BoundSocket bound = bind(limitedHandler, socket);
        String oversized = "{\"action\":\"PING\",\"padding\":\"012345678901234567890123456789\"}";
        bound.textHandler.handle(oversized);

        verify(parser, never()).parse(anyString());
        verify(repository, never()).findByIdAndClientId(org.mockito.ArgumentMatchers.any(), anyString());
        verify(socket).close(eq((short) 1009), eq("Message too big"));
        assertThat(registry.containsConnection(socket)).isFalse();
    }

    @Test
    void messageDirectlyBelowLimitIsAccepted() throws Exception {
        ServerWebSocket socket = openSocket();
        WebSocketMessageParser parser = mock(WebSocketMessageParser.class);
        int limit = 96;
        String message = pingMessageWithByteSize(limit);
        when(parser.parse(message)).thenReturn(new WebSocketMessageParser.ClientMessage("PING", null));
        TaskWebSocketHandler limitedHandler = new TaskWebSocketHandler(new GetTaskUseCase(repository), registry, parser, limit);

        BoundSocket bound = bind(limitedHandler, socket);
        bound.textHandler.handle(message);

        verify(parser).parse(message);
        verify(socket, never()).close(eq((short) 1009), anyString());
        verify(socket).writeTextMessage(org.mockito.ArgumentMatchers.argThat(response -> response.contains("\"type\":\"PONG\"")));
    }

    @Test
    void handlerLimitUsesUtf8BytesNotCharacterCount() throws Exception {
        ServerWebSocket socket = openSocket();
        WebSocketMessageParser parser = mock(WebSocketMessageParser.class);
        TaskWebSocketHandler limitedHandler = new TaskWebSocketHandler(new GetTaskUseCase(repository), registry, parser, 28);

        BoundSocket bound = bind(limitedHandler, socket);
        bound.textHandler.handle("{\"action\":\"PING\",\"x\":\"ёёёё\"}");

        verify(parser, never()).parse(anyString());
        verify(socket).close(eq((short) 1009), eq("Message too big"));
    }

    private BoundSocket bind(ServerWebSocket socket) {
        return bind(handler, socket);
    }

    private BoundSocket bind(TaskWebSocketHandler handlerToBind, ServerWebSocket socket) {
        handlerToBind.bind(socket, CLIENT_ID);
        ArgumentCaptor<Handler<Void>> closeCaptor = ArgumentCaptor.forClass(Handler.class);
        ArgumentCaptor<Handler<String>> textCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(socket).closeHandler(closeCaptor.capture());
        verify(socket).textMessageHandler(textCaptor.capture());
        return new BoundSocket(closeCaptor.getValue(), textCaptor.getValue());
    }

    private static ServerWebSocket openSocket() {
        ServerWebSocket socket = mock(ServerWebSocket.class);
        when(socket.isClosed()).thenReturn(false);
        when(socket.writeQueueFull()).thenReturn(false);
        return socket;
    }

    private static String subscribeMessage(UUID taskId) {
        return "{\"action\":\"SUBSCRIBE\",\"taskId\":\"" + taskId + "\"}";
    }

    private static String pingMessageWithByteSize(int sizeBytes) {
        String prefix = "{\"action\":\"PING\",\"padding\":\"";
        String suffix = "\"}";
        return prefix + "x".repeat(sizeBytes - prefix.length() - suffix.length()) + suffix;
    }

    private record BoundSocket(Handler<Void> closeHandler, Handler<String> textHandler) {
    }
}
