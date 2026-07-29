package ru.spb.aiagent.web.exception;

/**
 * Имя header/context поля correlation id.
 */
public final class CorrelationId {
    /** Header correlation id. */
    public static final String HEADER = "X-Correlation-Id";
    /** RoutingContext key correlation id. */
    public static final String KEY = "correlationId";

    private CorrelationId() {
    }
}
