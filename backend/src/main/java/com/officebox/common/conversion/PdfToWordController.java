package com.officebox.common.conversion;

import com.officebox.common.task.Task;
import com.officebox.common.task.TaskRunner;
import com.officebox.common.task.TaskService;
import com.officebox.common.task.TaskProgress;
import com.officebox.common.storage.StorageProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
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
  private final StorageProperties storage;

  public PdfToWordController(TaskService taskService, TaskRunner taskRunner, PdfToWordService converter, StorageProperties storage) {
    this.taskService = taskService;
    this.taskRunner = taskRunner;
    this.converter = converter;
    this.storage = storage;
  }

  @PostMapping(value = "/to-word", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Task convert(@RequestParam("file") MultipartFile file) throws Exception {
    if (file.isEmpty()) throw new IllegalArgumentException("PDF file is required");
    String original = file.getOriginalFilename() == null ? "document.pdf" : Path.of(file.getOriginalFilename()).getFileName().toString();
    if (!original.toLowerCase().endsWith(".pdf")) throw new IllegalArgumentException("Input must be a PDF");

    Path inputDir = Path.of(storage.root()).toAbsolutePath().normalize().resolve("input");
    Path outputDir = Path.of(storage.root()).toAbsolutePath().normalize().resolve("output");
    Files.createDirectories(inputDir);
    Files.createDirectories(outputDir);
    UUID id = UUID.randomUUID();
    Path input = inputDir.resolve(id + "-" + original).normalize();
    Files.write(input, file.getBytes());

    Task task = taskService.create("PDF_TO_WORD", input.toString());
    taskRunner.submit(task.id(), progress -> {
      Path result = converter.convert(input, outputDir.resolve(task.id().toString()), progress);
      taskService.markSuccess(task.id(), result.toString());
    });
    return task;
  }
}
