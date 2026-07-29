package ru.spb.aiagent.infrastructure.config;

/**
 * Конфигурация приложения из переменных окружения с безопасными локальными defaults.
 */
public record AppConfig(
        int httpPort,
        String databaseHost,
        int databasePort,
        String databaseName,
        String databaseUser,
        String databasePassword,
        int databasePoolSize,
        String jdbcUrl,
        int aiStepDelayMs,
        int aiTimeoutMs) {

    /**
     * Читает и валидирует конфигурацию из окружения.
     */
    public static AppConfig fromEnv() {
        int dbPort = intEnv("DATABASE_PORT", 5432);
        String dbName = strEnv("DATABASE_NAME", "ai_agent");
        String dbUser = strEnv("DATABASE_USER", "ai_agent");
        String dbPassword = strEnv("DATABASE_PASSWORD", "ai_agent");
        String jdbc = strEnv("DATABASE_JDBC_URL", "jdbc:postgresql://" + strEnv("DATABASE_HOST", "localhost") + ":" + dbPort + "/" + dbName);
        return new AppConfig(
                intEnv("HTTP_PORT", 8080),
                strEnv("DATABASE_HOST", "localhost"),
                dbPort,
                dbName,
                dbUser,
                dbPassword,
                intEnv("DATABASE_POOL_SIZE", 10),
                jdbc,
                intEnv("AI_STEP_DELAY_MS", 150),
                intEnv("AI_TIMEOUT_MS", 7000));
    }

    private static String strEnv(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
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
