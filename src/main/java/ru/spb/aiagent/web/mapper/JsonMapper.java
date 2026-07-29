package ru.spb.aiagent.web.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Фабрика Jackson ObjectMapper с поддержкой java.time.
 */
public final class JsonMapper {
    private JsonMapper() {
    }

    /**
     * Создаёт mapper для REST и WebSocket сообщений.
     */
    public static ObjectMapper create() {
        return new ObjectMapper().registerModule(new JavaTimeModule()).findAndRegisterModules();
    }
}
