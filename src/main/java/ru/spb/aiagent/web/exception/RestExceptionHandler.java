package ru.spb.aiagent.web.exception;

import ru.spb.aiagent.web.dto.ErrorResponse;
import ru.spb.aiagent.web.mapper.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.ext.web.RoutingContext;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Централизованный REST error handler без stack trace и SQL-текста в API.
 */
public class RestExceptionHandler implements io.vertx.core.Handler<RoutingContext> {
    private final ObjectMapper mapper = JsonMapper.create();
    private final DomainToHttpErrorMapper errorMapper = new DomainToHttpErrorMapper();

    /**
     * Пишет ErrorResponse в response.
     */
    @Override
    public void handle(RoutingContext ctx) {
        Throwable error = ctx.failure() == null ? new IllegalStateException("Unknown error") : ctx.failure();
        int status = errorMapper.status(error);
        String message = status == 500 ? "Internal server error" : error.getMessage();
        ErrorResponse body = new ErrorResponse(errorMapper.code(error), message, ctx.get(CorrelationId.KEY), List.of(), OffsetDateTime.now(ZoneOffset.UTC));
        try {
            ctx.response().setStatusCode(status).putHeader("Content-Type", "application/json").end(mapper.writeValueAsString(body));
        } catch (Exception e) {
            ctx.response().setStatusCode(500).end();
        }
    }
}
