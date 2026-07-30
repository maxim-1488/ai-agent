package ru.spb.aiagent.application.usecase;

import ru.spb.aiagent.application.core.AiClient;
import ru.spb.aiagent.application.core.TaskEventPublisher;
import ru.spb.aiagent.application.core.TaskEventType;
import ru.spb.aiagent.application.core.TaskExecutionRegistry;
import ru.spb.aiagent.application.core.TaskRepository;
import ru.spb.aiagent.domain.exception.TaskConflictException;
import ru.spb.aiagent.domain.exception.TaskNotFoundException;
import ru.spb.aiagent.domain.model.Task;
import ru.spb.aiagent.domain.model.TaskFilter;
import ru.spb.aiagent.domain.model.TaskPage;
import ru.spb.aiagent.domain.model.TaskStatus;
import io.vertx.core.Future;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import ru.spb.aiagent.infrastructure.ai.AiTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ExecuteTaskUseCaseTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-28T10:00:00Z");

    @Test
    void executesSuccessfullyFromInProgressToCompleted() {
        Fixture fixture = fixture((taskId, prompt, progressCallback, cancellationToken) -> Future.succeededFuture("result"));
        Task task = fixture.createdTask();

        fixture.useCase.executeAsync(task);

        Task saved = fixture.repository.task(task.id());
        assertThat(saved.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(saved.result()).isEqualTo("result");
        assertThat(saved.progress()).isEqualTo(100);
        assertThat(fixture.publisher.types()).containsExactly("TASK_STARTED", "TASK_COMPLETED");
    }

    @Test
    void updatesAndPublishesProgress() {
        Fixture fixture = fixture((taskId, prompt, progressCallback, cancellationToken) ->
                progressCallback.onProgress(40).map("result"));
        Task task = fixture.createdTask();

        fixture.useCase.executeAsync(task);

        assertThat(fixture.publisher.types()).contains("TASK_PROGRESS");
        Task progressEventTask = fixture.publisher.taskFor();
        assertThat(progressEventTask.progress()).isEqualTo(40);
    }

    @Test
    void marksTaskFailedOnAiExecutionError() {
        Fixture fixture = fixture((taskId, prompt, progressCallback, cancellationToken) ->
                Future.failedFuture(new RuntimeException("AI failed")));
        Task task = fixture.createdTask();

        fixture.useCase.executeAsync(task);

        Task saved = fixture.repository.task(task.id());
        assertThat(saved.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(saved.errorMessage()).isEqualTo("AI failed");
        assertThat(fixture.publisher.types()).contains("TASK_FAILED");
    }

    @Test
    void marksTaskTimedOutOnAiTimeout() {
        Fixture fixture = fixture((taskId, prompt, progressCallback, cancellationToken) ->
                Future.failedFuture(new AiTimeoutException("AI timeout")));
        Task task = fixture.createdTask();

        fixture.useCase.executeAsync(task);

        Task saved = fixture.repository.task(task.id());
        assertThat(saved.status()).isEqualTo(TaskStatus.TIMED_OUT);
        assertThat(saved.errorMessage()).isEqualTo("AI timeout");
    }

    @Test
    void publishesTimedOutEventForTimeoutInsteadOfFailed() {
        Fixture fixture = fixture((taskId, prompt, progressCallback, cancellationToken) ->
                Future.failedFuture(new AiTimeoutException("AI timeout")));
        Task task = fixture.createdTask();

        fixture.useCase.executeAsync(task);

        assertThat(fixture.publisher.types()).contains("TASK_TIMED_OUT");
        assertThat(fixture.publisher.types()).doesNotContain("TASK_FAILED");
    }

    @Test
    void doesNotTurnCancellationIntoFailed() {
        Fixture fixture = fixture((taskId, prompt, progressCallback, cancellationToken) -> {
            UUID id = UUID.fromString(taskId);
            fixtureRef.get().registry.cancel(id);
            return fixtureRef.get().repository.cancel(id, "client")
                    .compose(ignored -> Future.failedFuture(new RuntimeException("AI execution was cancelled")));
        });
        fixtureRef.set(fixture);
        Task task = fixture.createdTask();

        fixture.useCase.executeAsync(task);

        Task saved = fixture.repository.task(task.id());
        assertThat(saved.status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(fixture.publisher.types()).doesNotContain("TASK_FAILED");
    }

    @Test
    void foreignClientCancelDoesNotAffectActiveExecutionProgressOrCompletion() {
        ControlledAiClient aiClient = new ControlledAiClient();
        Fixture fixture = fixture(aiClient);
        Task task = fixture.createdTask();
        CancelTaskUseCase cancel = new CancelTaskUseCase(fixture.repository, fixture.registry, fixture.publisher);

        fixture.useCase.executeAsync(task);

        Future<Task> cancelResult = cancel.cancel("other-client", task.id());

        assertThat(cancelResult.failed()).isTrue();
        assertThat(cancelResult.cause()).isInstanceOf(TaskNotFoundException.class);
        assertThat(fixture.registry.isCancelled(task.id())).isFalse();

        aiClient.progressCallback.onProgress(40);
        aiClient.result.complete("result");

        Task saved = fixture.repository.task(task.id());
        assertThat(saved.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(saved.progress()).isEqualTo(100);
        assertThat(saved.result()).isEqualTo("result");
        assertThat(fixture.publisher.types()).contains("TASK_PROGRESS", "TASK_COMPLETED");
        assertThat(fixture.publisher.types()).doesNotContain("TASK_CANCELLED");
    }

    @Test
    void doesNotRunTaskWhenRegistryRejectsDuplicateRegistration() {
        Fixture fixture = fixture((taskId, prompt, progressCallback, cancellationToken) -> Future.succeededFuture("result"));
        fixture.registry.registerResult = false;
        Task task = fixture.createdTask();

        fixture.useCase.executeAsync(task);

        assertThat(fixture.aiClient.runCount).isZero();
        assertThat(fixture.repository.markInProgressCount).isZero();
        assertThat(fixture.registry.unregisterCount).isZero();
    }

    @Test
    void unregistersAfterSuccess() {
        Fixture fixture = fixture((taskId, prompt, progressCallback, cancellationToken) -> Future.succeededFuture("result"));
        Task task = fixture.createdTask();

        fixture.useCase.executeAsync(task);

        assertThat(fixture.registry.unregisterCount).isEqualTo(1);
        assertThat(fixture.registry.active).doesNotContain(task.id());
    }

    @Test
    void unregistersAfterFailure() {
        Fixture fixture = fixture((taskId, prompt, progressCallback, cancellationToken) ->
                Future.failedFuture(new RuntimeException("AI failed")));
        Task task = fixture.createdTask();

        fixture.useCase.executeAsync(task);

        assertThat(fixture.registry.unregisterCount).isEqualTo(1);
        assertThat(fixture.registry.active).doesNotContain(task.id());
    }

    @Test
    void keepsTerminalStateWhenTerminalEventPublicationFails() {
        Fixture fixture = fixture((taskId, prompt, progressCallback, cancellationToken) -> Future.succeededFuture("result"));
        fixture.publisher.failTypes.add("TASK_COMPLETED");
        Task task = fixture.createdTask();

        fixture.useCase.executeAsync(task);

        Task saved = fixture.repository.task(task.id());
        assertThat(saved.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(fixture.registry.unregisterCount).isEqualTo(1);
    }

    @Test
    void doesNotRunAiClientWhenOptimisticLockVersionConflicts() {
        Fixture fixture = fixture((taskId, prompt, progressCallback, cancellationToken) -> Future.succeededFuture("result"));
        Task task = fixture.createdTask();
        Task staleTask = new Task(
                task.id(),
                task.clientId(),
                task.prompt(),
                task.status(),
                task.progress(),
                task.result(),
                task.errorMessage(),
                task.createdAt(),
                task.startedAt(),
                task.completedAt(),
                task.updatedAt(),
                task.version() + 1);

        fixture.useCase.executeAsync(staleTask);

        assertThat(fixture.aiClient.runCount).isZero();
        assertThat(fixture.repository.task(task.id()).status()).isEqualTo(TaskStatus.CREATED);
        assertThat(fixture.registry.unregisterCount).isEqualTo(1);
    }

    private static final ThreadLocal<Fixture> fixtureRef = new ThreadLocal<>();

    private Fixture fixture(AiClient aiClient) {
        RecordingRepository repository = new RecordingRepository();
        RecordingRegistry registry = new RecordingRegistry();
        RecordingPublisher publisher = new RecordingPublisher();
        CountingAiClient countingAiClient = new CountingAiClient(aiClient);
        ExecuteTaskUseCase useCase = new ExecuteTaskUseCase(repository, countingAiClient, registry, publisher);
        return new Fixture(repository, registry, publisher, countingAiClient, useCase);
    }

    private record Fixture(
            RecordingRepository repository,
            RecordingRegistry registry,
            RecordingPublisher publisher,
            CountingAiClient aiClient,
            ExecuteTaskUseCase useCase) {
        Task createdTask() {
            Task task = Task.create("client", "prompt", NOW);
            repository.tasks.put(task.id(), task);
            return task;
        }
    }

    private record PublishedEvent(String type, Task task) {
    }

    private static class CountingAiClient implements AiClient {
        private final AiClient delegate;
        private int runCount;

        CountingAiClient(AiClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public Future<String> run(String taskId, String prompt, ProgressCallback progressCallback, CancellationToken cancelToken) {
            runCount++;
            return delegate.run(taskId, prompt, progressCallback, cancelToken);
        }
    }

    private static class ControlledAiClient implements AiClient {
        private final io.vertx.core.Promise<String> result = io.vertx.core.Promise.promise();
        private ProgressCallback progressCallback;

        @Override
        public Future<String> run(String taskId, String prompt, ProgressCallback progressCallback, CancellationToken cancelToken) {
            this.progressCallback = progressCallback;
            return result.future();
        }
    }

    private static class RecordingPublisher implements TaskEventPublisher {
        private final List<PublishedEvent> events = new ArrayList<>();
        private final Set<String> failTypes = new HashSet<>();

        @Override
        public Future<Void> publish(String type, Task task) {
            events.add(new PublishedEvent(type, task));
            if (failTypes.contains(type)) {
                return Future.failedFuture(new RuntimeException("publish failed: " + type));
            }
            return Future.succeededFuture();
        }

        List<String> types() {
            return events.stream().map(PublishedEvent::type).toList();
        }

        Task taskFor() {
            return events.stream()
                    .filter(event -> event.type().equals(TaskEventType.PROGRESS.wireType()))
                    .findFirst()
                    .orElseThrow()
                    .task();
        }
    }

    private static class RecordingRegistry implements TaskExecutionRegistry {
        private final Set<UUID> active = ConcurrentHashMap.newKeySet();
        private final Set<UUID> cancelled = ConcurrentHashMap.newKeySet();
        private boolean registerResult = true;
        private int unregisterCount;

        @Override
        public boolean register(UUID taskId) {
            if (!registerResult) {
                return false;
            }
            cancelled.remove(taskId);
            return active.add(taskId);
        }

        @Override
        public void cancel(UUID taskId) {
            cancelled.add(taskId);
        }

        @Override
        public boolean isCancelled(UUID taskId) {
            return cancelled.contains(taskId);
        }

        @Override
        public void unregister(UUID taskId) {
            unregisterCount++;
            active.remove(taskId);
            cancelled.remove(taskId);
        }
    }

    private static class RecordingRepository implements TaskRepository {
        private final Map<UUID, Task> tasks = new ConcurrentHashMap<>();
        private int markInProgressCount;

        @Override
        public Future<Task> create(Task task) {
            tasks.put(task.id(), task);
            return Future.succeededFuture(task);
        }

        @Override
        public Future<Task> findByIdAndClientId(UUID id, String clientId) {
            Task task = tasks.get(id);
            if (task == null || !task.clientId().equals(clientId)) {
                return Future.failedFuture(new TaskNotFoundException("not found"));
            }
            return Future.succeededFuture(task);
        }

        @Override
        public Future<TaskPage> list(String clientId, TaskFilter filter) {
            return Future.succeededFuture(new TaskPage(List.of(), 0, filter.page(), filter.size()));
        }

        @Override
        public Future<List<Task>> listRecoverable() {
            return Future.succeededFuture(tasks.values().stream()
                    .filter(task -> task.status() == TaskStatus.CREATED || task.status() == TaskStatus.IN_PROGRESS)
                    .toList());
        }

        @Override
        public Future<Task> markInProgress(UUID id, long version) {
            markInProgressCount++;
            Task task = task(id);
            if (task.version() != version || task.status() != TaskStatus.CREATED) {
                return Future.failedFuture(new TaskConflictException("version conflict"));
            }
            Task updated = copy(task, TaskStatus.IN_PROGRESS, task.progress(), null, null);
            tasks.put(id, updated);
            return Future.succeededFuture(updated);
        }

        @Override
        public Future<Task> resetInProgressForRecovery(UUID id, long version) {
            Task task = task(id);
            if (task.version() != version || task.status() != TaskStatus.IN_PROGRESS) {
                return Future.failedFuture(new TaskConflictException("recovery conflict"));
            }
            Task updated = copy(task, TaskStatus.CREATED, 0, null, null);
            tasks.put(id, updated);
            return Future.succeededFuture(updated);
        }

        @Override
        public Future<Task> updateProgress(UUID id, int progress) {
            Task task = task(id);
            if (task.status() != TaskStatus.IN_PROGRESS) {
                return Future.failedFuture(new TaskConflictException("progress conflict"));
            }
            Task updated = copy(task, task.status(), Math.max(task.progress(), progress), task.result(), task.errorMessage());
            tasks.put(id, updated);
            return Future.succeededFuture(updated);
        }

        @Override
        public Future<Task> complete(UUID id, TaskStatus status, String result, String errorMessage) {
            Task task = task(id);
            if (task.status() != TaskStatus.IN_PROGRESS || status == TaskStatus.CANCELLED || !status.isTerminal()) {
                return Future.failedFuture(new TaskConflictException("complete conflict"));
            }
            int progress = status == TaskStatus.COMPLETED ? 100 : task.progress();
            Task updated = copy(task, status, progress, result, errorMessage);
            tasks.put(id, updated);
            return Future.succeededFuture(updated);
        }

        @Override
        public Future<Task> cancel(UUID id, String clientId) {
            Task task = task(id);
            if (!task.clientId().equals(clientId)) {
                return Future.failedFuture(new TaskNotFoundException("not found"));
            }
            if (task.status().isTerminal()) {
                return Future.failedFuture(new TaskConflictException("cancel conflict"));
            }
            Task updated = copy(task, TaskStatus.CANCELLED, task.progress(), null, null);
            tasks.put(id, updated);
            return Future.succeededFuture(updated);
        }

        private Task task(UUID id) {
            Task task = tasks.get(id);
            if (task == null) {
                throw new TaskNotFoundException("not found");
            }
            return task;
        }

        private Task copy(Task task, TaskStatus status, int progress, String result, String errorMessage) {
            OffsetDateTime startedAt = task.startedAt() == null && status == TaskStatus.IN_PROGRESS ? NOW : task.startedAt();
            OffsetDateTime completedAt = status.isTerminal() ? NOW : task.completedAt();
            return new Task(
                    task.id(),
                    task.clientId(),
                    task.prompt(),
                    status,
                    progress,
                    result,
                    errorMessage,
                    task.createdAt(),
                    startedAt,
                    completedAt,
                    NOW,
                    task.version() + 1);
        }
    }
}
