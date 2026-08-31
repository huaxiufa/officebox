package com.officebox.common.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.officebox.common.storage.StorageProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskServiceTest {
  @TempDir
  Path tempDir;

  private TaskService service() {
    return new TaskService(new ObjectMapper(), new StorageProperties(tempDir.toString(), 24));
  }

  @Test
  void taskLifecycleIsTrackedAndPersisted() throws Exception {
    TaskService service = service();
    Task created = service.create("PDF_MERGE", "/tmp/input.pdf");

    assertEquals(TaskStatus.QUEUED, created.status());
    assertEquals(TaskStatus.PROCESSING, service.markProcessing(created.id()).status());
    Task progressed = service.updateProgress(created.id(), new TaskProgress(65, "Converting"));
    assertEquals(65, progressed.progress());
    assertEquals("Converting", progressed.progressMessage());
    Task withResult = service.setResult(created.id(), "task-1/result.docx");
    assertEquals("task-1/result.docx", withResult.resultFile());
    Task success = service.markSuccess(created.id(), withResult.resultFile());
    assertEquals(TaskStatus.SUCCESS, success.status());
    assertEquals(100, success.progress());

    TaskService reloaded = new TaskService(new ObjectMapper(), new StorageProperties(tempDir.toString(), 24));
    Task persisted = reloaded.get(created.id());
    assertEquals(TaskStatus.SUCCESS, persisted.status());
    assertEquals(100, persisted.progress());
    assertEquals("task-1/result.docx", persisted.resultFile());
    assertTrue(Files.readString(tempDir.resolve("tasks.json")).contains(created.id().toString()));
  }

  @Test
  void failedTaskKeepsInput() {
    TaskService service = service();
    Task created = service.create("OCR", "/tmp/input.pdf");
    Task failed = service.markFailed(created.id(), "processing failed");

    assertEquals(TaskStatus.FAILED, failed.status());
    assertEquals(created.inputFile(), failed.inputFile());
    assertEquals("processing failed", failed.error());
  }

  @Test
  void rejectsProgressOutsideRange() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> new TaskProgress(101, "too far"));
  }
}
