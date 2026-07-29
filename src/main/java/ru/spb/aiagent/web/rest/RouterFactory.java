package ru.spb.aiagent.web.rest;

import ru.spb.aiagent.web.exception.CorrelationIdHandler;
import ru.spb.aiagent.web.exception.RestExceptionHandler;
import ru.spb.aiagent.web.websocket.TaskWebSocketHandler;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;

/**
 * Фабрика HTTP router с REST, WebSocket и health endpoints.
 */
public class RouterFactory {
    /**
     * Создаёт router приложения.
     */
    public Router create(io.vertx.core.Vertx vertx, TaskRestHandler rest, TaskWebSocketHandler ws) {
        Router router = Router.router(vertx);
        router.route().handler(cors());
        router.route().handler(new CorrelationIdHandler());
        router.route().handler(BodyHandler.create());
        router.get("/health").handler(ok());
        router.post("/api/v1/tasks").handler(rest::create);
        router.get("/api/v1/tasks").handler(rest::list);
        router.get("/api/v1/tasks/:taskId").handler(rest::get);
        router.post("/api/v1/tasks/:taskId/cancel").handler(rest::cancel);
        router.get("/ws/tasks").handler(ws);
        router.route().failureHandler(new RestExceptionHandler());
        return router;
    }

    private Handler<RoutingContext> ok() {
        return ctx -> ctx.response().putHeader("Content-Type", "application/json").end("{\"status\":\"UP\"}");
    }

    private CorsHandler cors() {
        return CorsHandler.create()
                .addOrigin("http://localhost:5173")
                .addOrigin("http://127.0.0.1:5173")
                .allowedMethod(HttpMethod.GET)
                .allowedMethod(HttpMethod.POST)
                .allowedMethod(HttpMethod.OPTIONS)
                .allowedHeader("Content-Type")
                .allowedHeader("X-Client-Id")
                .allowedHeader("X-Correlation-Id")
                .exposedHeader("X-Correlation-Id")
                .maxAgeSeconds(3600);
    }
}
