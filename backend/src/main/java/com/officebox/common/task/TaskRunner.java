package com.officebox.common.task;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
    return CompletableFuture.supplyAsync(() -> {
      taskService.markProcessing(taskId);
      try {
        String resultFile = operation.get();
        if (resultFile == null || resultFile.isBlank()) {
          throw new IllegalStateException("Task completed without a result file");
        }
        return taskService.markSuccess(taskId, resultFile);
      } catch (RuntimeException e) {
        taskService.markFailed(taskId, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        throw e;
      }
    }, executor);
  }
}
