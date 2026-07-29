package ru.spb.aiagent.web.exception;

import io.vertx.ext.web.RoutingContext;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * REST handler, который проставляет correlation id в response header и MDC.
 */
public class CorrelationIdHandler implements io.vertx.core.Handler<RoutingContext> {
    /**
     * Обрабатывает запрос и передаёт управление дальше.
     */
    @Override
    public void handle(RoutingContext ctx) {
        String id = ctx.request().getHeader(CorrelationId.HEADER);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        ctx.put(CorrelationId.KEY, id);
        ctx.response().putHeader(CorrelationId.HEADER, id);
        MDC.put(CorrelationId.KEY, id);
        ctx.addEndHandler(v -> MDC.remove(CorrelationId.KEY));
        ctx.next();
    }
}
