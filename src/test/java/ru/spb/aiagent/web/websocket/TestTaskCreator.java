package ru.spb.aiagent.web.websocket;

import ru.spb.aiagent.domain.model.Task;
import ru.spb.aiagent.domain.model.TaskStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

final class TestTaskCreator {
    private TestTaskCreator() {
    }

    static Task task(UUID taskId, String clientId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Task(taskId, clientId, "prompt", TaskStatus.IN_PROGRESS, 50, null, null, now, now, null, now, 1);
    }
}
