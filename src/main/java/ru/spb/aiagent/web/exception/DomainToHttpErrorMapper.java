package ru.spb.aiagent.web.exception;

import ru.spb.aiagent.domain.exception.InvalidProgressException;
import ru.spb.aiagent.domain.exception.InvalidTaskStateException;
import ru.spb.aiagent.domain.exception.TaskConflictException;
import ru.spb.aiagent.domain.exception.TaskNotFoundException;

/**
 * Mapper исключений в HTTP status и application error code.
 */
public class DomainToHttpErrorMapper {
    /**
     * Возвращает HTTP status.
     */
    public int status(Throwable error) {
        if (error instanceof IllegalArgumentException || error instanceof InvalidProgressException) {
            return 400;
        }
        if (error instanceof TaskNotFoundException) {
            return 404;
        }
        if (error instanceof TaskConflictException || error instanceof InvalidTaskStateException) {
            return 409;
        }
        return 500;
    }

    /**
     * Возвращает стабильный error code.
     */
    public String code(Throwable error) {
        return switch (status(error)) {
            case 400 -> "VALIDATION_ERROR";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            default -> "INTERNAL_ERROR";
        };
    }
}
