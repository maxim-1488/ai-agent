package ru.spb.aiagent.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Единый формат ошибки REST API. */
public record ErrorResponse(String errorCode, String message, String correlationId, List<ErrorDetail> details, OffsetDateTime timestamp) {
}
