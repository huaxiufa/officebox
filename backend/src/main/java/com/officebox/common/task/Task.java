package com.officebox.common.task;

import java.time.Instant;
import java.util.UUID;

public record Task(
    UUID id,
    String type,
    TaskStatus status,
    String inputFile,
    String resultFile,
    String error,
    Instant createdAt,
    Instant updatedAt
) {
  public static Task queued(String type, String inputFile) {
    Instant now = Instant.now();
    return new Task(UUID.randomUUID(), type, TaskStatus.QUEUED, inputFile, null, null, now, now);
  }
}
