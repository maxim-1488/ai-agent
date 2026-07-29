package ru.spb.aiagent.application.core;

import ru.spb.aiagent.domain.model.Task;

/**
 * Порт публикации событий задач во внешние адаптеры, включая WebSocket.
 */
public interface TaskEventPublisher {
    /**
     * Публикует событие задачи после успешного изменения состояния.
     *
     * @param type тип события
     * @param task актуальная задача
     * @return асинхронное завершение публикации
     */
    io.vertx.core.Future<Void> publish(String type, Task task);
}
