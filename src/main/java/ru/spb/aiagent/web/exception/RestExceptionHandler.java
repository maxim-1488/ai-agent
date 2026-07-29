package ru.spb.aiagent.web.exception;

import ru.spb.aiagent.web.dto.ErrorResponse;
import ru.spb.aiagent.web.mapper.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.ext.web.RoutingContext;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Централизованный REST error handler без stack trace и SQL-текста в API.
 */
public class RestExceptionHandler implements io.vertx.core.Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);
    private final ObjectMapper mapper = JsonMapper.create();
    private final DomainToHttpErrorMapper errorMapper = new DomainToHttpErrorMapper();

    /**
     * Пишет ErrorResponse в response.
     */
    @Override
    public void handle(RoutingContext ctx) {
        Throwable error = ctx.failure() == null ? new IllegalStateException("Unknown error") : ctx.failure();
        int status = errorMapper.status(error);
        String correlationId = ctx.get(CorrelationId.KEY);
        logFailure(ctx, status, correlationId, error);
        String message = status == 500 ? "Internal server error" : error.getMessage();
        ErrorResponse body = new ErrorResponse(errorMapper.code(error), message, correlationId, List.of(), OffsetDateTime.now(ZoneOffset.UTC));
        try {
            ctx.response().setStatusCode(status).putHeader("Content-Type", "application/json").end(mapper.writeValueAsString(body));
        } catch (Exception e) {
            log.error("Failed to serialize REST error response: correlationId={}", correlationId, e);
            ctx.response().setStatusCode(500).end();
        }
    }

    private void logFailure(RoutingContext ctx, int status, String correlationId, Throwable error) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable(CorrelationId.KEY, correlationId == null ? "" : correlationId)) {
            String method = ctx.request().method().name();
            String path = ctx.normalizedPath();
            if (status >= 500) {
                log.error("Unhandled REST error: method={}, path={}, status={}, correlationId={}",
                        method, path, status, correlationId, error);
            } else if (status == 409 || status == 403) {
                log.warn("REST request rejected: method={}, path={}, status={}, correlationId={}, reason={}",
                        method, path, status, correlationId, error.getMessage());
            } else {
                log.debug("REST request failed with expected client error: method={}, path={}, status={}, correlationId={}, reason={}",
                        method, path, status, correlationId, error.getMessage());
            }
        }
    }
}
