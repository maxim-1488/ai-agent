package ru.spb.aiagent.domain.model;

/**
 * Статус AI-задачи и правила терминальности состояния.
 */
public enum TaskStatus {
    CREATED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMED_OUT;

    /**
     * Возвращает true, если состояние терминальное и дальнейшие изменения запрещены.
     *
     * @return признак терминального состояния
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == TIMED_OUT;
    }
}
