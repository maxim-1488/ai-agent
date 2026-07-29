package ru.spb.aiagent.infrastructure.repository;

import ru.spb.aiagent.domain.model.Task;
import ru.spb.aiagent.domain.model.TaskStatus;
import io.vertx.sqlclient.Row;

/**
 * Mapper строки PostgreSQL в доменную модель Task.
 */
public class TaskRowMapper {
    /**
     * Преобразует Row в Task без утечки SQL-типов в application/domain.
     */
    public Task map(Row row) {
        return new Task(
                row.getUUID("id"),
                row.getString("client_id"),
                row.getString("prompt"),
                TaskStatus.valueOf(row.getString("status")),
                row.getInteger("progress"),
                row.getString("result"),
                row.getString("error_message"),
                row.getOffsetDateTime("created_at"),
                row.getOffsetDateTime("started_at"),
                row.getOffsetDateTime("completed_at"),
                row.getOffsetDateTime("updated_at"),
                row.getLong("version"));
    }
}
