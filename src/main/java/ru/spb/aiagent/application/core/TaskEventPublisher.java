package ru.spb.aiagent.application.core;

import ru.spb.aiagent.domain.model.Task;
import io.vertx.core.Future;

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
    Future<Void> publish(String type, Task task);

    /**
     * Публикует событие задачи с типом из централизованной модели application слоя.
     *
     * <p>Метод сохраняет совместимость с существующим строковым контрактом адаптеров,
     * но убирает строковые литералы из бизнес-потока use case.
     *
     * @param type тип события задачи
     * @param task актуальная задача
     * @return асинхронное завершение публикации
     */
    default Future<Void> publish(TaskEventType type, Task task) {
        return publish(type.wireType(), task);
    }
}
