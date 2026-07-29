package ru.spb.aiagent.domain.model;

import ru.spb.aiagent.domain.exception.InvalidProgressException;
import ru.spb.aiagent.domain.exception.InvalidTaskStateException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Доменная модель AI-задачи с инвариантами статуса, прогресса и optimistic locking version.
 *
 * <p>Терминальные состояния {@code COMPLETED}, {@code FAILED}, {@code CANCELLED},
 * {@code TIMED_OUT} не должны изменяться. Прогресс всегда находится в диапазоне 0..100.
 */
public record Task(
        UUID id,
        String clientId,
        String prompt,
        TaskStatus status,
        int progress,
        String result,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime updatedAt,
        long version) {

    /**
     * Создаёт валидную доменную задачу.
     */
    public Task {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (clientId.isBlank()) {
            throw new IllegalArgumentException("clientId is blank");
        }
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("prompt is blank");
        }
        validateProgress(progress);
    }

    /**
     * Создаёт новую задачу в состоянии CREATED.
     *
     * @param clientId идентификатор клиента
     * @param prompt исходный запрос
     * @param now время создания
     * @return новая задача
     */
    public static Task create(String clientId, String prompt, OffsetDateTime now) {
        return new Task(UUID.randomUUID(), clientId, prompt, TaskStatus.CREATED, 0, null, null, now, null, null, now, 0);
    }

    /**
     * Проверяет допустимость перехода статуса.
     *
     * @param next следующий статус
     */
    public void requireTransition(TaskStatus next) {
        if (status.isTerminal()) {
            throw new InvalidTaskStateException("Terminal task cannot be changed");
        }
        boolean allowed = (status == TaskStatus.CREATED && (next == TaskStatus.IN_PROGRESS || next == TaskStatus.CANCELLED))
                || (status == TaskStatus.IN_PROGRESS && (next == TaskStatus.COMPLETED || next == TaskStatus.FAILED
                || next == TaskStatus.CANCELLED || next == TaskStatus.TIMED_OUT || next == TaskStatus.IN_PROGRESS));
        if (!allowed) {
            throw new InvalidTaskStateException("Invalid status transition " + status + " -> " + next);
        }
    }

    /**
     * Проверяет диапазон прогресса.
     *
     * @param progress значение прогресса
     */
    public static void validateProgress(int progress) {
        if (progress < 0 || progress > 100) {
            throw new InvalidProgressException("Progress must be in range 0..100");
        }
    }
}
