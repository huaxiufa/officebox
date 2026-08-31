package com.officebox.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StorageCleanupScheduler {
  private final StorageProperties properties;
  private final StorageService storageService;

  public StorageCleanupScheduler(StorageProperties properties, StorageService storageService) {
    this.properties = properties;
    this.storageService = storageService;
  }

  @Scheduled(fixedDelayString = "PT1H")
  public void cleanup() throws IOException {
    Path root = storageService.root();
    if (!Files.exists(root)) return;
    Instant cutoff = Instant.now().minus(properties.maxRetentionHours(), ChronoUnit.HOURS);
    try (Stream<Path> paths = Files.walk(root)) {
      paths.filter(Files::isRegularFile)
          .filter(path -> !path.equals(root.resolve("tasks.json")))
          .filter(path -> {
            try { return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff); }
            catch (IOException e) { return false; }
          })
          .forEach(path -> {
            try { Files.deleteIfExists(path); }
            catch (IOException ignored) { }
          });
    }
  }
}
