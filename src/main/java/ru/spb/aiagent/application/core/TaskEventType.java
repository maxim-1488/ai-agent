package ru.spb.aiagent.application.core;

import ru.spb.aiagent.domain.model.TaskStatus;

/**
 * Централизованный перечень событий жизненного цикла задачи.
 *
 * <p>Enum хранит соответствие между внутренними бизнес-событиями application слоя
 * и строковыми типами сообщений, которые используются внешними адаптерами,
 * например WebSocket и журналом событий в БД.
 */
public enum TaskEventType {
    CREATED("TASK_CREATED"),
    STARTED("TASK_STARTED"),
    PROGRESS("TASK_PROGRESS"),
    COMPLETED("TASK_COMPLETED"),
    FAILED("TASK_FAILED"),
    TIMED_OUT("TASK_TIMED_OUT"),
    CANCELLED("TASK_CANCELLED");

    private final String wireType;

    TaskEventType(String wireType) {
        this.wireType = wireType;
    }

    /**
     * Возвращает строковый тип события для внешних контрактов.
     *
     * @return тип события, используемый в WebSocket и persisted events
     */
    public String wireType() {
        return wireType;
    }

    /**
     * Возвращает terminal event, соответствующий фактическому terminal status задачи.
     *
     * @param status terminal status задачи
     * @return тип terminal event
     * @throws IllegalArgumentException если status не является поддерживаемым terminal status
     */
    public static TaskEventType fromTerminalStatus(TaskStatus status) {
        return switch (status) {
            case COMPLETED -> COMPLETED;
            case FAILED -> FAILED;
            case TIMED_OUT -> TIMED_OUT;
            case CANCELLED -> CANCELLED;
            default -> throw new IllegalArgumentException("Unsupported terminal task status: " + status);
        };
    }
}
