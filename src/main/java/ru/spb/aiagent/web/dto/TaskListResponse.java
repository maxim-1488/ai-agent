package ru.spb.aiagent.web.dto;

import java.util.List;

/** Ответ REST API со страницей задач. */
public record TaskListResponse(List<TaskResponse> items, long total, int page, int size) {
}
