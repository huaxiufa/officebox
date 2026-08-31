package com.officebox.common.task;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TaskService {
  private final Map<UUID, Task> tasks = new ConcurrentHashMap<>();

  public Task create(String type, String inputFile) {
    Task task = Task.queued(type, inputFile);
    tasks.put(task.id(), task);
    return task;
  }

  public Task get(UUID id) {
    return tasks.get(id);
  }

  public Task markProcessing(UUID id) {
    return update(id, TaskStatus.PROCESSING, null, null);
  }

  public Task markSuccess(UUID id, String resultFile) {
    return update(id, TaskStatus.SUCCESS, resultFile, null);
  }

  public Task markFailed(UUID id, String error) {
    return update(id, TaskStatus.FAILED, null, error);
  }

  private Task update(UUID id, TaskStatus status, String resultFile, String error) {
    return tasks.computeIfPresent(id, (key, current) ->
        new Task(current.id(), current.type(), status, current.inputFile(),
            resultFile == null ? current.resultFile() : resultFile,
            error, current.createdAt(), java.time.Instant.now()));
  }
}
