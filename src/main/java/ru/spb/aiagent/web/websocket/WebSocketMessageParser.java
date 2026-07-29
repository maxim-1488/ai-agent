package ru.spb.aiagent.web.websocket;

import ru.spb.aiagent.web.mapper.JsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

/**
 * Parser клиентских WebSocket-сообщений.
 */
public class WebSocketMessageParser {
    private final ObjectMapper json = JsonMapper.create();

    /**
     * Парсит JSON и валидирует action/taskId.
     */
    public ClientMessage parse(String raw) throws Exception {
        JsonNode node = json.readTree(raw);
        String action = node.path("action").asText("");
        UUID taskId = node.hasNonNull("taskId") ? UUID.fromString(node.get("taskId").asText()) : null;
        return new ClientMessage(action, taskId);
    }

    /**
     * Клиентское WebSocket-сообщение.
     */
    public record ClientMessage(String action, UUID taskId) {
        /**
         * Создаёт сообщение клиента.
         */
        public ClientMessage {
        }
    }
}
