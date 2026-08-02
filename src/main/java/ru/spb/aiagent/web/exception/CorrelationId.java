package ru.spb.aiagent.web.exception;

/**
 * Имя header/context поля correlation id.
 */
public final class CorrelationId {
    /** Имя HTTP header для correlation id. */
    public static final String HEADER = "X-Correlation-Id";
    /** Ключ correlation id в RoutingContext. */
    public static final String KEY = "correlationId";

    private CorrelationId() {
    }
}
