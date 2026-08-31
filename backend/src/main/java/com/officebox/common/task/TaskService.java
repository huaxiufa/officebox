package com.officebox.common.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.officebox.common.storage.StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TaskService {
  private final Map<UUID, Task> tasks = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper;
  private final Path taskFile;

  public TaskService(ObjectMapper objectMapper, StorageProperties properties) {
    this.objectMapper = objectMapper;
    this.taskFile = Path.of(properties.root()).toAbsolutePath().normalize().resolve("tasks.json");
    load();
  }

  public synchronized Task create(String type, String inputFile) {
    Task task = Task.queued(type, inputFile);
    tasks.put(task.id(), task);
    persist();
    return task;
  }

  public Task get(UUID id) {
    return tasks.get(id);
  }

  public synchronized Task markProcessing(UUID id) {
    return update(id, TaskStatus.PROCESSING, null, null);
  }

  public synchronized Task markSuccess(UUID id, String resultFile) {
    return update(id, TaskStatus.SUCCESS, resultFile, null);
  }

  public synchronized Task markFailed(UUID id, String error) {
    return update(id, TaskStatus.FAILED, null, error);
  }

  private Task update(UUID id, TaskStatus status, String resultFile, String error) {
    Task updated = tasks.computeIfPresent(id, (key, current) ->
        new Task(current.id(), current.type(), status, current.inputFile(),
            resultFile == null ? current.resultFile() : resultFile,
            error, current.createdAt(), java.time.Instant.now()));
    if (updated != null) persist();
    return updated;
  }

  private void load() {
    try {
      if (!Files.exists(taskFile)) return;
      List<Task> saved = objectMapper.readValue(taskFile.toFile(), new TypeReference<>() {});
      saved.forEach(task -> tasks.put(task.id(), task));
    } catch (IOException ignored) {
      // A corrupt task journal must not prevent the application from starting.
    }
  }

  private void persist() {
    try {
      Files.createDirectories(taskFile.getParent());
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(taskFile.toFile(), new ArrayList<>(tasks.values()));
    } catch (IOException e) {
      throw new IllegalStateException("Unable to persist task state", e);
    }
  }
}
