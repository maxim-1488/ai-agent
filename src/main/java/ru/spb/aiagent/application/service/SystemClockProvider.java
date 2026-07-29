package ru.spb.aiagent.application.service;

import ru.spb.aiagent.application.core.ClockProvider;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Системная реализация порта времени в UTC.
 */
public class SystemClockProvider implements ClockProvider {
    /**
     * Возвращает текущее UTC-время.
     */
    @Override
    public OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
