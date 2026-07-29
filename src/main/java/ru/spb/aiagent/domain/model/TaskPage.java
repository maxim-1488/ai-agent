package ru.spb.aiagent.domain.model;

import java.util.List;

/**
 * Страница задач текущего клиента.
 */
public record TaskPage(List<Task> items, long total, int page, int size) {
    /**
     * Создаёт страницу задач.
     */
    public TaskPage {
        items = List.copyOf(items);
    }
}
