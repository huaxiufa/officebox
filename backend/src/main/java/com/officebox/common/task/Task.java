package com.officebox.common.task;

import java.time.Instant;
import java.util.UUID;

public record Task(
    UUID id,
    String type,
    TaskStatus status,
    int progress,
    String progressMessage,
    String inputFile,
    String resultFile,
    String error,
    Instant createdAt,
    Instant updatedAt
) {
  public Task {
    if (progress < 0 || progress > 100) {
      throw new IllegalArgumentException("progress must be between 0 and 100");
    }
  }

  public static Task queued(String type, String inputFile) {
    Instant now = Instant.now();
    return new Task(UUID.randomUUID(), type, TaskStatus.QUEUED, 0, "Queued", inputFile, null, null, now, now);
  }
}
