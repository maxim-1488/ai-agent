package ru.spb.aiagent.web.dto;

/** Деталь ошибки валидации REST API. */
public record ErrorDetail(String field, String message) {
}
