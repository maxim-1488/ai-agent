package ru.spb.aiagent.infrastructure.config;

/**
 * Конфигурация PostgreSQL для Liquibase и реактивного PostgreSQL-клиента Vert.x.
 */
public record DatabaseConfig(
        String host,
        int port,
        String name,
        String user,
        String password,
        int poolSize,
        String jdbcUrl) {

    /**
     * Читает и валидирует конфигурацию PostgreSQL из окружения.
     */
    public static DatabaseConfig fromEnv() {
        int port = intEnv("DATABASE_PORT", 5432);
        String host = strEnv("DATABASE_HOST", "localhost");
        String name = strEnv("DATABASE_NAME", "ai_agent");
        return new DatabaseConfig(
                host,
                port,
                name,
                strEnv("DATABASE_USER", "ai_agent"),
                strEnv("DATABASE_PASSWORD", "ai_agent"),
                intEnv("DATABASE_POOL_SIZE", 10),
                strEnv("DATABASE_JDBC_URL", "jdbc:postgresql://" + host + ":" + port + "/" + name));
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
