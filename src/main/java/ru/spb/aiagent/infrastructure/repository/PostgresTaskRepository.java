package ru.spb.aiagent.infrastructure.repository;

import ru.spb.aiagent.application.core.TaskRepository;
import ru.spb.aiagent.application.core.TaskEventType;
import ru.spb.aiagent.domain.exception.TaskConflictException;
import ru.spb.aiagent.domain.exception.TaskNotFoundException;
import ru.spb.aiagent.domain.model.Task;
import ru.spb.aiagent.domain.model.TaskFilter;
import ru.spb.aiagent.domain.model.TaskPage;
import ru.spb.aiagent.domain.model.TaskStatus;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostgreSQL-адаптер TaskRepository на vertx-pg-client.
 *
 * <p>Все запросы параметризованы, сортировка проходит whitelist, атомарные операции используют
 * transaction boundaries, а терминальные состояния защищены SQL-условиями.
 */
public class PostgresTaskRepository implements TaskRepository {
    private static final Logger log = LoggerFactory.getLogger(PostgresTaskRepository.class);
    private static final String COLUMNS = "id, client_id, prompt, status, progress, result, error_message, created_at, started_at, completed_at, updated_at, version";
    private final Pool pool;
    private final TaskRowMapper mapper = new TaskRowMapper();

    /**
     * Создаёт repository-адаптер поверх общего PgPool.
     */
    public PostgresTaskRepository(Pool pool) {
        this.pool = pool;
    }

    /**
     * Создаёт задачу и событие TASK_CREATED в одной транзакции.
     */
    @Override
    public Future<Task> create(Task task) {
        log.debug("Repository create task: taskId={}, clientId={}, status={}", task.id(), task.clientId(), task.status());
        String insert = "INSERT INTO ai_task (" + COLUMNS + ") VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12) RETURNING " + COLUMNS;
        return pool.withTransaction(conn -> conn.preparedQuery(insert).execute(Tuple.of(
                        task.id(), task.clientId(), task.prompt(), task.status().name(), task.progress(), task.result(), task.errorMessage(),
                        task.createdAt(), task.startedAt(), task.completedAt(), task.updatedAt(), task.version()))
                .compose(rows -> {
                    Task saved = one(rows);
                    return insertEvent(conn, saved, TaskEventType.CREATED.wireType()).map(saved);
                }))
                .onFailure(error -> log.error("Repository create task failed: taskId={}, clientId={}", task.id(), task.clientId(), error));
    }

    /**
     * Ищет задачу по id и clientId.
     */
    @Override
    public Future<Task> findByIdAndClientId(UUID id, String clientId) {
        log.debug("Repository find task: taskId={}, clientId={}", id, clientId);
        return pool.preparedQuery("SELECT " + COLUMNS + " FROM ai_task WHERE id=$1 AND client_id=$2")
                .execute(Tuple.of(id, clientId)).map(this::requiredOne);
    }

    /**
     * Возвращает список задач с безопасной сортировкой.
     */
    @Override
    public Future<TaskPage> list(String clientId, TaskFilter filter) {
        log.debug("Repository list tasks: clientId={}, page={}, size={}, status={}, sort={}, direction={}",
                clientId, filter.page(), filter.size(), filter.status(), filter.sort(), filter.direction());
        String sort = PostgresTaskQueries.sortColumn(filter.sort()) + " " + filter.direction().name();
        String where = filter.status() == null ? "client_id=$1" : "client_id=$1 AND status=$2";
        Tuple dataParams = filter.status() == null
                ? Tuple.of(clientId, filter.size(), filter.page() * filter.size())
                : Tuple.of(clientId, filter.status().name(), filter.size(), filter.page() * filter.size());
        Tuple countParams = filter.status() == null ? Tuple.of(clientId) : Tuple.of(clientId, filter.status().name());
        String dataSql = "SELECT " + COLUMNS + " FROM ai_task WHERE " + where + " ORDER BY " + sort + " LIMIT $" + (filter.status() == null ? 2 : 3) + " OFFSET $" + (filter.status() == null ? 3 : 4);
        String countSql = "SELECT count(*) AS total FROM ai_task WHERE " + where;
        return pool.preparedQuery(dataSql).execute(dataParams).compose(rows -> {
            ArrayList<Task> tasks = new ArrayList<>();
            for (Row row : rows) {
                tasks.add(mapper.map(row));
            }
            return pool.preparedQuery(countSql).execute(countParams)
                    .map(countRows -> new TaskPage(tasks, countRows.iterator().next().getLong("total"), filter.page(), filter.size()));
        });
    }

    /**
     * Переводит задачу в IN_PROGRESS с проверкой version и статуса CREATED.
     */
    @Override
    public Future<Task> markInProgress(UUID id, long version) {
        log.debug("Repository mark task in progress: taskId={}, expectedVersion={}", id, version);
        OffsetDateTime now = now();
        return pool.preparedQuery("UPDATE ai_task SET status='IN_PROGRESS', started_at=$1, updated_at=$1, version=version+1 "
                        + "WHERE id=$2 AND version=$3 AND status='CREATED' RETURNING " + COLUMNS)
                .execute(Tuple.of(now, id, version)).map(rows -> conflictAwareOne(rows, "markInProgress", id));
    }

    /**
     * Обновляет progress только вперёд для активной задачи.
     */
    @Override
    public Future<Task> updateProgress(UUID id, int progress) {
        Task.validateProgress(progress);
        log.debug("Repository update task progress: taskId={}, progress={}", id, progress);
        OffsetDateTime now = now();
        return pool.preparedQuery("UPDATE ai_task SET progress=GREATEST(progress,$1), updated_at=$2, version=version+1 "
                        + "WHERE id=$3 AND status='IN_PROGRESS' RETURNING " + COLUMNS)
                .execute(Tuple.of(progress, now, id)).map(rows -> conflictAwareOne(rows, "updateProgress", id));
    }

    /**
     * Завершает задачу терминальным статусом и пишет событие в транзакции.
     */
    @Override
    public Future<Task> complete(UUID id, TaskStatus status, String result, String errorMessage) {
        if (!status.isTerminal() || status == TaskStatus.CANCELLED) {
            return Future.failedFuture(new IllegalArgumentException("Invalid terminal status"));
        }
        OffsetDateTime now = now();
        String eventType = TaskEventType.fromTerminalStatus(status).wireType();
        log.debug("Repository complete task: taskId={}, terminalStatus={}", id, status);
        return pool.withTransaction(conn -> conn.preparedQuery("UPDATE ai_task SET status=$1, progress=CASE WHEN $1::varchar='COMPLETED' THEN 100 ELSE progress END, "
                        + "result=$2, error_message=$3, completed_at=$4, updated_at=$4, version=version+1 "
                + "WHERE id=$5 AND status='IN_PROGRESS' RETURNING " + COLUMNS)
                .execute(Tuple.of(status.name(), result, errorMessage, now, id))
                .map(rows -> conflictAwareOne(rows, "complete", id))
                .compose(task -> insertEvent(conn, task, eventType).map(task)));
    }

    /**
     * Отменяет задачу текущего клиента и пишет событие в транзакции.
     */
    @Override
    public Future<Task> cancel(UUID id, String clientId) {
        log.debug("Repository cancel task: taskId={}, clientId={}", id, clientId);
        OffsetDateTime now = now();
        return pool.withTransaction(conn -> conn.preparedQuery("UPDATE ai_task SET status='CANCELLED', completed_at=$1, updated_at=$1, version=version+1 "
                        + "WHERE id=$2 AND client_id=$3 AND status IN ('CREATED','IN_PROGRESS') RETURNING " + COLUMNS)
                .execute(Tuple.of(now, id, clientId))
                .map(rows -> conflictAwareOne(rows, "cancel", id))
                .compose(task -> insertEvent(conn, task, TaskEventType.CANCELLED.wireType()).map(task)));
    }

    private Future<RowSet<Row>> insertEvent(io.vertx.sqlclient.SqlConnection conn, Task task, String type) {
        String payload = "{\"taskId\":\"" + task.id() + "\",\"status\":\"" + task.status() + "\",\"progress\":" + task.progress() + "}";
        return conn.preparedQuery("INSERT INTO task_event (id, task_id, client_id, type, payload, created_at) VALUES ($1,$2,$3,$4,$5::jsonb,$6)")
                .execute(Tuple.of(UUID.randomUUID(), task.id(), task.clientId(), type, payload, now()));
    }

    private Task requiredOne(RowSet<Row> rows) {
        if (!rows.iterator().hasNext()) {
            throw new TaskNotFoundException("Task not found");
        }
        return mapper.map(rows.iterator().next());
    }

    private Task conflictAwareOne(RowSet<Row> rows, String operation, UUID taskId) {
        if (!rows.iterator().hasNext()) {
            log.warn("Repository optimistic update affected 0 rows: operation={}, taskId={}", operation, taskId);
            throw new TaskConflictException("Task was already changed or is in terminal state");
        }
        log.debug("Repository optimistic update affected 1 row: operation={}, taskId={}", operation, taskId);
        return mapper.map(rows.iterator().next());
    }

    private Task one(RowSet<Row> rows) {
        return mapper.map(rows.iterator().next());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
