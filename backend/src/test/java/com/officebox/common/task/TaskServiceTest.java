package com.officebox.common.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    Task success = service.markSuccess(created.id(), tempDir.resolve("output/result.pdf").toString());
    assertEquals(TaskStatus.SUCCESS, success.status());
    assertNotNull(success.resultFile());
    TaskService reloaded = new TaskService(new ObjectMapper(), new StorageProperties(tempDir.toString(), 24));
    assertEquals(success.status(), reloaded.get(created.id()).status());
    assertNotNull(Files.readString(tempDir.resolve("tasks.json")));
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
}
