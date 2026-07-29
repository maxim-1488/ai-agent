package ru.spb.aiagent.application.usecase;

import ru.spb.aiagent.application.core.ClockProvider;
import ru.spb.aiagent.application.core.TaskEventPublisher;
import ru.spb.aiagent.application.core.TaskEventType;
import ru.spb.aiagent.application.core.TaskRepository;
import ru.spb.aiagent.application.service.InMemoryTaskExecutionRegistry;
import ru.spb.aiagent.domain.exception.TaskConflictException;
import ru.spb.aiagent.domain.exception.TaskNotFoundException;
import ru.spb.aiagent.domain.model.Task;
import ru.spb.aiagent.domain.model.TaskFilter;
import ru.spb.aiagent.domain.model.TaskPage;
import ru.spb.aiagent.domain.model.TaskStatus;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

    @Test
    void foreignClientCancelDoesNotSignalRegistryOrPublishCancelledEvent() {
        FakeRepository repo = new FakeRepository();
        RecordingRegistry registry = new RecordingRegistry();
        RecordingPublisher publisher = new RecordingPublisher();
        Task task = Task.create("client-a", "prompt", OffsetDateTime.now(ZoneOffset.UTC));
        repo.tasks.put(task.id(), inProgress(task));
        CancelTaskUseCase cancel = new CancelTaskUseCase(repo, registry, publisher);

        Future<Task> result = cancel.cancel("client-b", task.id());

        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(TaskNotFoundException.class);
        assertThat(registry.isCancelled(task.id())).isFalse();
        assertThat(repo.tasks.get(task.id()).status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(publisher.types()).doesNotContain(TaskEventType.CANCELLED.wireType());
    }

    @Test
    void ownerCancelSignalsRegistryOnlyAfterRepositoryConfirmsCancellation() {
        Task task = Task.create("client", "prompt", OffsetDateTime.now(ZoneOffset.UTC));
        Task inProgress = inProgress(task);
        Task cancelled = cancelled(inProgress);
        DeferredCancelRepository repo = new DeferredCancelRepository(inProgress.id(), "client");
        RecordingRegistry registry = new RecordingRegistry();
        RecordingPublisher publisher = new RecordingPublisher();
        CancelTaskUseCase cancel = new CancelTaskUseCase(repo, registry, publisher);

        Future<Task> result = cancel.cancel("client", inProgress.id());

        assertThat(registry.isCancelled(inProgress.id())).isFalse();

        repo.cancelPromise.complete(cancelled);

        assertThat(result.result().status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(registry.isCancelled(inProgress.id())).isTrue();
        assertThat(publisher.types()).containsExactly(TaskEventType.CANCELLED.wireType());
    }

    @Test
    void repeatedCancelKeepsConflictSemanticsAndDoesNotRepublishEvent() {
        FakeRepository repo = new FakeRepository();
        RecordingRegistry registry = new RecordingRegistry();
        RecordingPublisher publisher = new RecordingPublisher();
        Task task = Task.create("client", "prompt", OffsetDateTime.now(ZoneOffset.UTC));
        Task cancelled = cancelled(task);
        repo.tasks.put(task.id(), cancelled);
        CancelTaskUseCase cancel = new CancelTaskUseCase(repo, registry, publisher);

        Future<Task> result = cancel.cancel("client", task.id());

        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(TaskConflictException.class);
        assertThat(registry.isCancelled(task.id())).isFalse();
        assertThat(publisher.types()).isEmpty();
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
            if (t.status() != TaskStatus.IN_PROGRESS) {
                return Future.failedFuture(new TaskConflictException("progress conflict"));
            }
            Task updated = new Task(t.id(), t.clientId(), t.prompt(), t.status(), progress, null, null, t.createdAt(), t.startedAt(), null, t.createdAt(), t.version() + 1);
            tasks.put(id, updated);
            return Future.succeededFuture(updated);
        }

        @Override
        public Future<Task> complete(UUID id, TaskStatus status, String result, String errorMessage) {
            Task t = tasks.get(id);
            if (t.status() != TaskStatus.IN_PROGRESS || status == TaskStatus.CANCELLED || !status.isTerminal()) {
                return Future.failedFuture(new TaskConflictException("complete conflict"));
            }
            Task updated = new Task(t.id(), t.clientId(), t.prompt(), status, status == TaskStatus.COMPLETED ? 100 : t.progress(), result, errorMessage, t.createdAt(), t.startedAt(), t.createdAt(), t.createdAt(), t.version() + 1);
            tasks.put(id, updated);
            return Future.succeededFuture(updated);
        }

        @Override
        public Future<Task> cancel(UUID id, String clientId) {
            Task t = tasks.get(id);
            if (t == null || !t.clientId().equals(clientId)) {
                return Future.failedFuture(new TaskNotFoundException("not found"));
            }
            if (t.status().isTerminal()) {
                return Future.failedFuture(new TaskConflictException("cancel conflict"));
            }
            Task updated = new Task(t.id(), t.clientId(), t.prompt(), TaskStatus.CANCELLED, t.progress(), null, null, t.createdAt(), t.startedAt(), t.createdAt(), t.createdAt(), t.version() + 1);
            tasks.put(id, updated);
            return Future.succeededFuture(updated);
        }
    }

    static class RecordingRegistry extends InMemoryTaskExecutionRegistry {
    }

    static class RecordingPublisher implements TaskEventPublisher {
        private final List<String> types = new ArrayList<>();

        @Override
        public Future<Void> publish(String type, Task task) {
            types.add(type);
            return Future.succeededFuture();
        }

        List<String> types() {
            return types;
        }
    }

    static class DeferredCancelRepository extends FakeRepository {
        private final UUID expectedTaskId;
        private final String expectedClientId;
        private final Promise<Task> cancelPromise = Promise.promise();

        DeferredCancelRepository(UUID expectedTaskId, String expectedClientId) {
            this.expectedTaskId = expectedTaskId;
            this.expectedClientId = expectedClientId;
        }

        @Override
        public Future<Task> cancel(UUID id, String clientId) {
            assertThat(id).isEqualTo(expectedTaskId);
            assertThat(clientId).isEqualTo(expectedClientId);
            return cancelPromise.future();
        }
    }

    private static Task inProgress(Task task) {
        return new Task(task.id(), task.clientId(), task.prompt(), TaskStatus.IN_PROGRESS, task.progress(), task.result(), task.errorMessage(),
                task.createdAt(), task.createdAt(), task.completedAt(), task.updatedAt(), task.version() + 1);
    }

    private static Task cancelled(Task task) {
        return new Task(task.id(), task.clientId(), task.prompt(), TaskStatus.CANCELLED, task.progress(), task.result(), task.errorMessage(),
                task.createdAt(), task.startedAt(), task.createdAt(), task.createdAt(), task.version() + 1);
    }
}
