package com.officebox.common.task;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class TaskRunner {
  private final TaskService taskService;
  private final Executor executor;

  public TaskRunner(TaskService taskService, @Qualifier("officeBoxTaskExecutor") Executor executor) {
    this.taskService = taskService;
    this.executor = executor;
  }

  public CompletableFuture<Task> run(UUID taskId, Supplier<String> operation) {
    return run(taskId, progress -> operation.get());
  }

  public CompletableFuture<Task> run(UUID taskId, Consumer<Consumer<TaskProgress>> operation) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        Task processing = taskService.markProcessing(taskId);
        if (processing == null) {
          throw new IllegalArgumentException("Task not found: " + taskId);
        }
        operation.accept(new ProgressReporter(taskId));
        Task current = taskService.get(taskId);
        if (current == null || current.resultFile() == null || current.resultFile().isBlank()) {
          throw new IllegalStateException("Task completed without a result file");
        }
        return taskService.markSuccess(taskId, current.resultFile());
      } catch (RuntimeException e) {
        taskService.markFailed(taskId, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        throw e;
      }
    }, executor);
  }

  private final class ProgressReporter implements Consumer<TaskProgress> {
    private final UUID taskId;

    private ProgressReporter(UUID taskId) {
      this.taskId = taskId;
    }

    @Override
    public void accept(TaskProgress progress) {
      taskService.updateProgress(taskId, progress);
    }
  }
}
