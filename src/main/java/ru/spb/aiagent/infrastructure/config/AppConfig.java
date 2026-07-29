package ru.spb.aiagent.infrastructure.config;

/**
 * Конфигурация приложения из переменных окружения с безопасными локальными defaults.
 */
public record AppConfig(
        int httpPort,
        DatabaseConfig database,
        int aiStepDelayMs,
        int aiTimeoutMs,
        int websocketMaxMessageSizeBytes) {
    private static final int DEFAULT_WEBSOCKET_MAX_MESSAGE_SIZE_BYTES = 8192;

    /**
     * Создаёт конфигурацию приложения.
     */
    public AppConfig {
        if (database == null) {
            throw new ConfigException("database configuration must not be null");
        }
    }

    /**
     * Читает и валидирует конфигурацию из окружения.
     */
    public static AppConfig fromEnv() {
        return new AppConfig(
                intEnv("HTTP_PORT", 8080),
                DatabaseConfig.fromEnv(),
                intEnv("AI_STEP_DELAY_MS", 150),
                intEnv("AI_TIMEOUT_MS", 7000),
                intEnv("WEBSOCKET_MAX_MESSAGE_SIZE_BYTES", DEFAULT_WEBSOCKET_MAX_MESSAGE_SIZE_BYTES));
    }

    private static int intEnv(String key, int fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new ConfigException(key + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new ConfigException(key + " must be a number");
        }
    }
}
