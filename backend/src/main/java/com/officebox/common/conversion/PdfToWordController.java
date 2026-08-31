package com.officebox.common.conversion;

import com.officebox.common.api.ApiResponse;
import com.officebox.common.storage.StorageService;
import com.officebox.common.task.Task;
import com.officebox.common.task.TaskRunner;
import com.officebox.common.task.TaskService;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v2/pdf")
public class PdfToWordController {
  private final TaskService taskService;
  private final TaskRunner taskRunner;
  private final PdfToWordService converter;
  private final StorageService storageService;

  public PdfToWordController(TaskService taskService, TaskRunner taskRunner, PdfToWordService converter,
      StorageService storageService) {
    this.taskService = taskService;
    this.taskRunner = taskRunner;
    this.converter = converter;
    this.storageService = storageService;
  }

  @PostMapping(value = "/to-word", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<Task> convert(@RequestParam("file") MultipartFile file) throws IOException {
    if (file.isEmpty()) {
      throw new IllegalArgumentException("PDF file is required");
    }
    String original = file.getOriginalFilename() == null
        ? "document.pdf"
        : Path.of(file.getOriginalFilename()).getFileName().toString();
    if (!original.toLowerCase().endsWith(".pdf")) {
      throw new IllegalArgumentException("Input must be a PDF");
    }

    Path input = storageService.storeInput(file);
    Path outputDir = storageService.root().resolve("output");
    Task task;
    try {
      task = taskService.create("PDF_TO_WORD", input.toString());
    } catch (RuntimeException e) {
      try {
        storageService.deleteInput(input);
      } catch (IOException ignored) {
        // Preserve the original task creation failure.
      }
      throw e;
    }

    Task createdTask = task;
    taskRunner.run(task.id(), progress -> {
      try {
        Path result = converter.convert(input, outputDir.resolve(createdTask.id().toString()), progress);
        taskService.setResult(createdTask.id(), storageService.relativizeOutput(result));
      } catch (IOException e) {
        throw new IllegalStateException(e.getMessage(), e);
      } finally {
        try {
          storageService.deleteInput(input);
        } catch (IOException ignored) {
          // Conversion result remains available even if cleanup fails.
        }
      }
    });
    return ApiResponse.success("task created", task);
  }
}
