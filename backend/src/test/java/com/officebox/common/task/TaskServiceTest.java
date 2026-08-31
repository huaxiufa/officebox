package com.officebox.common.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class TaskServiceTest {
  @Test
  void taskLifecycleIsTracked() {
    TaskService service = new TaskService();
    Task created = service.create("PDF_MERGE", "/tmp/input.pdf");

    assertEquals(TaskStatus.QUEUED, created.status());
    assertEquals(TaskStatus.PROCESSING, service.markProcessing(created.id()).status());
    Task success = service.markSuccess(created.id(), "/tmp/output.pdf");
    assertEquals(TaskStatus.SUCCESS, success.status());
    assertNotNull(success.resultFile());
  }

  @Test
  void failedTaskKeepsInput() {
    TaskService service = new TaskService();
    Task created = service.create("OCR", "/tmp/input.pdf");
    Task failed = service.markFailed(created.id(), "processing failed");

    assertEquals(TaskStatus.FAILED, failed.status());
    assertEquals(created.inputFile(), failed.inputFile());
    assertEquals("processing failed", failed.error());
  }
}
