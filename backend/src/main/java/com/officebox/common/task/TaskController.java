package com.officebox.common.task;

import com.officebox.common.api.ApiResponse;
import com.officebox.common.storage.StorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v2/tasks")
public class TaskController {
  private final TaskService taskService;
  private final StorageService storageService;

  public TaskController(TaskService taskService, StorageService storageService) {
    this.taskService = taskService;
    this.storageService = storageService;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<Task> create(
      @RequestParam String type,
      @RequestParam("file") MultipartFile file) throws IOException {
    if (file.isEmpty()) {
      throw new IllegalArgumentException("File must not be empty");
    }
    return ApiResponse.success("task created", taskService.create(type, storageService.storeInput(file).toString()));
  }

  @GetMapping("/{id}")
  public ApiResponse<Task> get(@PathVariable UUID id) {
    Task task = taskService.get(id);
    if (task == null) {
      throw new IllegalArgumentException("Task not found: " + id);
    }
    return ApiResponse.success(task);
  }

  @GetMapping("/{id}/result")
  public ResponseEntity<Resource> result(@PathVariable UUID id) throws IOException {
    Task task = taskService.get(id);
    if (task == null) return ResponseEntity.notFound().build();
    if (task.status() != TaskStatus.SUCCESS || task.resultFile() == null || task.resultFile().isBlank()) {
      return ResponseEntity.status(409).build();
    }
    Path result = storageService.resolveOutput(task.resultFile());
    if (!Files.isRegularFile(result)) return ResponseEntity.notFound().build();
    String filename = result.getFileName().toString().replace("\"", "").replace("\r", "").replace("\n", "");
    Resource resource = new FileSystemResource(result);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(Files.size(result))
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .body(resource);
  }
}
