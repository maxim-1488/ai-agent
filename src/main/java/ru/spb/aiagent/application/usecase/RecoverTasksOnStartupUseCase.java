package ru.spb.aiagent.application.usecase;

import ru.spb.aiagent.application.core.TaskRepository;
import ru.spb.aiagent.domain.exception.TaskConflictException;
import ru.spb.aiagent.domain.model.Task;
import ru.spb.aiagent.domain.model.TaskStatus;
import io.vertx.core.Future;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Сценарий восстановления задач, оставшихся нетерминальными после перезапуска приложения.
 */
public class RecoverTasksOnStartupUseCase {
    private static final Logger log = LoggerFactory.getLogger(RecoverTasksOnStartupUseCase.class);

    private final TaskRepository repository;
    private final ExecuteTaskUseCase executor;

    /**
     * Создаёт сценарий восстановления незавершённых задач.
     */
    public RecoverTasksOnStartupUseCase(TaskRepository repository, ExecuteTaskUseCase executor) {
        this.repository = repository;
        this.executor = executor;
    }

    /**
     * Перезапускает CREATED-задачи и атомарно возвращает IN_PROGRESS-задачи в CREATED перед повторным запуском.
     */
    public Future<Void> recover() {
        return repository.listRecoverable()
                .compose(this::recoverAll)
                .onSuccess(ignored -> log.info("Startup task recovery completed"));
    }

    private Future<Void> recoverAll(List<Task> tasks) {
        log.info("Startup task recovery found recoverable tasks: count={}", tasks.size());
        Future<Void> chain = Future.succeededFuture();
        for (Task task : tasks) {
            chain = chain.compose(ignored -> recoverOne(task));
        }
        return chain;
    }

    private Future<Void> recoverOne(Task task) {
        if (task.status() == TaskStatus.CREATED) {
            log.info("Startup task recovery restarting created task: taskId={}, version={}", task.id(), task.version());
            executor.executeAsync(task);
            return Future.succeededFuture();
        }
        if (task.status() == TaskStatus.IN_PROGRESS) {
            return repository.resetInProgressForRecovery(task.id(), task.version())
                    .compose(reset -> {
                        log.info("Startup task recovery reset in-progress task: taskId={}, previousVersion={}, recoveredVersion={}",
                                task.id(), task.version(), reset.version());
                        executor.executeAsync(reset);
                        return Future.<Void>succeededFuture();
                    })
                    .recover(error -> recoverConflict(task, error));
        }
        return Future.succeededFuture();
    }

    private Future<Void> recoverConflict(Task task, Throwable error) {
        if (error instanceof TaskConflictException) {
            log.warn("Startup task recovery skipped changed task: taskId={}, status={}, version={}",
                    task.id(), task.status(), task.version());
            return Future.succeededFuture();
        }
        return Future.failedFuture(error);
    }
}
