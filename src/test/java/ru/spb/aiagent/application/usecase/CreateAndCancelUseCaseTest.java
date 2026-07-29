package ru.spb.aiagent.application.usecase;

import ru.spb.aiagent.application.core.ClockProvider;
import ru.spb.aiagent.application.core.TaskRepository;
import ru.spb.aiagent.application.service.InMemoryTaskExecutionRegistry;
import ru.spb.aiagent.domain.exception.TaskNotFoundException;
import ru.spb.aiagent.domain.model.Task;
import ru.spb.aiagent.domain.model.TaskFilter;
import ru.spb.aiagent.domain.model.TaskPage;
import ru.spb.aiagent.domain.model.TaskStatus;
import io.vertx.core.Future;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CreateAndCancelUseCaseTest {
    @Test
    void createsAndSchedulesTask() {
        FakeRepository repo = new FakeRepository();
        InMemoryTaskExecutionRegistry registry = new InMemoryTaskExecutionRegistry();
        ExecuteTaskUseCase execute = new ExecuteTaskUseCase(repo, (id, prompt, cb, token) -> Future.succeededFuture("ok"), registry, (type, task) -> Future.succeededFuture());
        CreateTaskUseCase create = new CreateTaskUseCase(repo, fixedClock(), execute, (type, task) -> Future.succeededFuture());

        Task task = create.create("client", "prompt").result();

        assertThat(task.clientId()).isEqualTo("client");
        assertThat(repo.tasks).containsKey(task.id());
    }

    @Test
    void cancelsOnlyKnownTask() {
        FakeRepository repo = new FakeRepository();
        Task task = Task.create("client", "prompt", OffsetDateTime.now(ZoneOffset.UTC));
        repo.tasks.put(task.id(), task);
        CancelTaskUseCase cancel = new CancelTaskUseCase(repo, new InMemoryTaskExecutionRegistry(), (type, t) -> Future.succeededFuture());

        Task cancelled = cancel.cancel("client", task.id()).result();

        assertThat(cancelled.status()).isEqualTo(TaskStatus.CANCELLED);
    }

    private ClockProvider fixedClock() {
        return () -> OffsetDateTime.parse("2026-07-28T10:00:00Z");
    }

    static class FakeRepository implements TaskRepository {
        final Map<UUID, Task> tasks = new ConcurrentHashMap<>();

        @Override
        public Future<Task> create(Task task) {
            tasks.put(task.id(), task);
            return Future.succeededFuture(task);
        }

        @Override
        public Future<Task> findByIdAndClientId(UUID id, String clientId) {
            Task task = tasks.get(id);
            return task == null || !task.clientId().equals(clientId) ? Future.failedFuture(new TaskNotFoundException("not found")) : Future.succeededFuture(task);
        }

        @Override
        public Future<TaskPage> list(String clientId, TaskFilter filter) {
            return Future.succeededFuture(new TaskPage(tasks.values().stream().filter(t -> t.clientId().equals(clientId)).toList(), tasks.size(), 0, 20));
        }

        @Override
        public Future<Task> markInProgress(UUID id, long version) {
            Task t = tasks.get(id);
            Task updated = new Task(t.id(), t.clientId(), t.prompt(), TaskStatus.IN_PROGRESS, 0, null, null, t.createdAt(), t.createdAt(), null, t.createdAt(), t.version() + 1);
            tasks.put(id, updated);
            return Future.succeededFuture(updated);
        }

        @Override
        public Future<Task> updateProgress(UUID id, int progress) {
            Task t = tasks.get(id);
            Task updated = new Task(t.id(), t.clientId(), t.prompt(), t.status(), progress, null, null, t.createdAt(), t.startedAt(), null, t.createdAt(), t.version() + 1);
            tasks.put(id, updated);
            return Future.succeededFuture(updated);
        }

        @Override
        public Future<Task> complete(UUID id, TaskStatus status, String result, String errorMessage) {
            Task t = tasks.get(id);
            Task updated = new Task(t.id(), t.clientId(), t.prompt(), status, status == TaskStatus.COMPLETED ? 100 : t.progress(), result, errorMessage, t.createdAt(), t.startedAt(), t.createdAt(), t.createdAt(), t.version() + 1);
            tasks.put(id, updated);
            return Future.succeededFuture(updated);
        }

        @Override
        public Future<Task> cancel(UUID id, String clientId) {
            Task t = tasks.get(id);
            Task updated = new Task(t.id(), t.clientId(), t.prompt(), TaskStatus.CANCELLED, t.progress(), null, null, t.createdAt(), t.startedAt(), t.createdAt(), t.createdAt(), t.version() + 1);
            tasks.put(id, updated);
            return Future.succeededFuture(updated);
        }
    }
}
