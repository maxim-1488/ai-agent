package ru.spb.aiagent.domain.model;

/**
 * Фильтр списка задач с whitelist-сортировкой, защищающей SQL от произвольной подстановки.
 */
public record TaskFilter(int page, int size, TaskStatus status, SortField sort, SortDirection direction) {
    /**
     * Разрешённые поля сортировки.
     */
    public enum SortField { CREATED_AT, UPDATED_AT, STATUS, PROGRESS }

    /**
     * Разрешённые направления сортировки.
     */
    public enum SortDirection { ASC, DESC }

    /**
     * Создаёт нормализованный фильтр.
     */
    public TaskFilter {
        if (page < 0) {
            page = 0;
        }
        if (size < 1) {
            size = 20;
        }
        if (size > 100) {
            size = 100;
        }
        if (sort == null) {
            sort = SortField.CREATED_AT;
        }
        if (direction == null) {
            direction = SortDirection.DESC;
        }
    }
}
