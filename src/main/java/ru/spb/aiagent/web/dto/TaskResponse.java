package ru.spb.aiagent.web.dto;

import ru.spb.aiagent.domain.model.TaskStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Ответ REST API с состоянием задачи. */
public record TaskResponse(UUID id, String prompt, TaskStatus status, int progress, String result, String errorMessage,
                           OffsetDateTime createdAt, OffsetDateTime startedAt, OffsetDateTime completedAt,
                           OffsetDateTime updatedAt, long version, String taskUrl, String webSocketUrl) {
}
