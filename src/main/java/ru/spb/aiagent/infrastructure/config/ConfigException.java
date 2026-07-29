package ru.spb.aiagent.infrastructure.config;

/**
 * Ошибка конфигурации, при которой приложение должно завершить bootstrap fail-fast.
 */
public class ConfigException extends RuntimeException {
    /**
     * Создаёт ошибку конфигурации.
     */
    public ConfigException(String message) {
        super(message);
    }
}
