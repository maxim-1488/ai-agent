package ru.spb.aiagent.application.core;

import java.time.OffsetDateTime;

/**
 * Порт времени, позволяющий тестировать сценарии без привязки к системным часам.
 */
public interface ClockProvider {
    /**
     * Возвращает текущее время.
     *
     * @return текущее время с offset
     */
    OffsetDateTime now();
}
