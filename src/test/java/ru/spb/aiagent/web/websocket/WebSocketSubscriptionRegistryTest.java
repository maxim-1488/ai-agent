package ru.spb.aiagent.web.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import org.mockito.ArgumentCaptor;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebSocketSubscriptionRegistryTest {

    private final WebSocketSubscriptionRegistry registry = new WebSocketSubscriptionRegistry();

    @Test
    void subscribeDoesNotRecreateUnregisteredConnection() {
        ServerWebSocket socket = openSocket();
        UUID taskId = UUID.randomUUID();

        registry.register(socket, "client-1");
        registry.unregister(socket);

        boolean subscribed = registry.subscribe(socket, taskId);

        assertThat(subscribed).isFalse();
        assertThat(registry.containsConnection(socket)).isFalse();
        assertThat(registry.containsSocketSubscriptions(socket)).isFalse();
        assertThat(registry.hasSubscription(socket, taskId)).isFalse();
        assertThat(registry.subscriberCount(taskId)).isZero();
    }

    @Test
    void subscribeKeepsOpenConnectionRegistered() {
        ServerWebSocket socket = openSocket();
        UUID taskId = UUID.randomUUID();

        registry.register(socket, "client-1");
        boolean subscribed = registry.subscribe(socket, taskId);

        assertThat(subscribed).isTrue();
        assertThat(registry.containsConnection(socket)).isTrue();
        assertThat(registry.containsSocketSubscriptions(socket)).isTrue();
        assertThat(registry.hasSubscription(socket, taskId)).isTrue();
        assertThat(registry.subscriberCount(taskId)).isOne();
    }

    @Test
    void unregisterRemovesCompletedSubscription() {
        ServerWebSocket socket = openSocket();
        UUID taskId = UUID.randomUUID();

        registry.register(socket, "client-1");
        registry.subscribe(socket, taskId);
        registry.unregister(socket);

        assertThat(registry.containsConnection(socket)).isFalse();
        assertThat(registry.containsSocketSubscriptions(socket)).isFalse();
        assertThat(registry.hasSubscription(socket, taskId)).isFalse();
        assertThat(registry.subscriberCount(taskId)).isZero();
    }

    @Test
    void unregisterRemovesAllTaskSubscriptionsForConnection() {
        ServerWebSocket socket = openSocket();
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();
        UUID taskC = UUID.randomUUID();

        registry.register(socket, "client-1");
        registry.subscribe(socket, taskA);
        registry.subscribe(socket, taskB);
        registry.subscribe(socket, taskC);
        registry.unregister(socket);

        assertThat(registry.hasSubscription(socket, taskA)).isFalse();
        assertThat(registry.hasSubscription(socket, taskB)).isFalse();
        assertThat(registry.hasSubscription(socket, taskC)).isFalse();
        assertThat(registry.subscriberCount(taskA)).isZero();
        assertThat(registry.subscriberCount(taskB)).isZero();
        assertThat(registry.subscriberCount(taskC)).isZero();
    }

    @Test
    void unregisterOneConnectionDoesNotRemoveOtherConnectionsForSameTask() {
        ServerWebSocket socket1 = openSocket();
        ServerWebSocket socket2 = openSocket();
        UUID taskId = UUID.randomUUID();

        registry.register(socket1, "client-1");
        registry.register(socket2, "client-1");
        registry.subscribe(socket1, taskId);
        registry.subscribe(socket2, taskId);
        registry.unregister(socket1);

        assertThat(registry.hasSubscription(socket1, taskId)).isFalse();
        assertThat(registry.hasSubscription(socket2, taskId)).isTrue();
        assertThat(registry.subscriberCount(taskId)).isOne();
    }

    @Test
    void publishSkipsUnregisteredConnections() {
        ServerWebSocket socket = openSocket();
        UUID taskId = UUID.randomUUID();

        registry.register(socket, "client-1");
        registry.subscribe(socket, taskId);
        registry.unregister(socket);
        registry.publish("TASK_UPDATED", TestTaskCreator.task(taskId, "client-1"));

        verify(socket, never()).writeTextMessage(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void heartbeatClosesConnectionAndCleansSubscriptionsWhenPongIsMissing() {
        ServerWebSocket socket = openSocket();
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();

        registry.register(socket, "client-1");
        registry.subscribe(socket, taskA);
        registry.subscribe(socket, taskB);

        registry.heartbeat(1_000, 500);
        registry.heartbeat(1_500, 500);

        verify(socket).writePing(org.mockito.ArgumentMatchers.any(Buffer.class));
        verify(socket).close();
        assertThat(registry.containsConnection(socket)).isFalse();
        assertThat(registry.containsSocketSubscriptions(socket)).isFalse();
        assertThat(registry.hasSubscription(socket, taskA)).isFalse();
        assertThat(registry.hasSubscription(socket, taskB)).isFalse();
        assertThat(registry.subscriberCount(taskA)).isZero();
        assertThat(registry.subscriberCount(taskB)).isZero();
    }

    @Test
    void heartbeatKeepsConnectionRegisteredWhenPongIsReceived() {
        ServerWebSocket socket = openSocket();
        UUID taskId = UUID.randomUUID();
        ArgumentCaptor<Handler<Buffer>> pongHandler = ArgumentCaptor.forClass(Handler.class);

        registry.register(socket, "client-1");
        verify(socket).pongHandler(pongHandler.capture());
        registry.subscribe(socket, taskId);

        registry.heartbeat(1_000, 500);
        pongHandler.getValue().handle(Buffer.buffer("1000"));
        registry.heartbeat(1_500, 500);

        verify(socket, never()).close();
        assertThat(registry.containsConnection(socket)).isTrue();
        assertThat(registry.containsSocketSubscriptions(socket)).isTrue();
        assertThat(registry.hasSubscription(socket, taskId)).isTrue();
        assertThat(registry.subscriberCount(taskId)).isOne();
    }

    private static ServerWebSocket openSocket() {
        ServerWebSocket socket = mock(ServerWebSocket.class);
        when(socket.isClosed()).thenReturn(false);
        when(socket.writeQueueFull()).thenReturn(false);
        when(socket.writePing(org.mockito.ArgumentMatchers.any(Buffer.class))).thenReturn(Future.succeededFuture());
        return socket;
    }
}
