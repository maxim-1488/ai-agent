package ru.spb.aiagent.infrastructure.repository;

import ru.spb.aiagent.domain.model.TaskFilter;

/**
 * SQL-фрагменты репозитория с whitelist-преобразованием сортировки.
 */
public final class PostgresTaskQueries {
    private PostgresTaskQueries() {
    }

    /**
     * Преобразует enum сортировки в разрешённое имя SQL-колонки.
     */
    public static String sortColumn(TaskFilter.SortField field) {
        return switch (field) {
            case CREATED_AT -> "created_at";
            case UPDATED_AT -> "updated_at";
            case STATUS -> "status";
            case PROGRESS -> "progress";
        };
    }
}
