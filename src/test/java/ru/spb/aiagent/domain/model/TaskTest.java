package ru.spb.aiagent.domain.model;

import ru.spb.aiagent.domain.exception.InvalidProgressException;
import ru.spb.aiagent.domain.exception.InvalidTaskStateException;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskTest {
    @Test
    void validatesTransitionsAndTerminalProtection() {
        Task task = Task.create("client", "prompt", OffsetDateTime.now(ZoneOffset.UTC));
        task.requireTransition(TaskStatus.IN_PROGRESS);

        Task completed = new Task(task.id(), task.clientId(), task.prompt(), TaskStatus.COMPLETED, 100,
                "ok", null, task.createdAt(), task.createdAt(), task.createdAt(), task.createdAt(), 1);
        assertThat(completed.status().isTerminal()).isTrue();
        assertThatThrownBy(() -> completed.requireTransition(TaskStatus.FAILED))
                .isInstanceOf(InvalidTaskStateException.class);
    }

    @Test
    void rejectsInvalidProgress() {
        assertThatThrownBy(() -> Task.validateProgress(101)).isInstanceOf(InvalidProgressException.class);
        assertThatThrownBy(() -> Task.validateProgress(-1)).isInstanceOf(InvalidProgressException.class);
    }
}
