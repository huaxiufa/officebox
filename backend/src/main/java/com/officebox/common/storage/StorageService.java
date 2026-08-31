package com.officebox.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StorageService {
  private final Path root;

  public StorageService(StorageProperties properties) {
    this.root = Path.of(properties.root()).toAbsolutePath().normalize();
  }

  public void initialize() throws IOException {
    Files.createDirectories(root.resolve("input"));
    Files.createDirectories(root.resolve("output"));
    Files.createDirectories(root.resolve("temporary"));
  }

  public Path storeInput(MultipartFile file) throws IOException {
    initialize();
    String original = file.getOriginalFilename() == null ? "file" : Path.of(file.getOriginalFilename()).getFileName().toString();
    Path target = root.resolve("input").resolve(UUID.randomUUID() + "-" + original).normalize();
    if (!target.startsWith(root.resolve("input"))) {
      throw new IOException("Invalid file path");
    }
    try (InputStream input = file.getInputStream()) {
      Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
    }
    return target;
  }

  public Path resolveOutput(String storedPath) throws IOException {
    Path output = root.resolve("output").toAbsolutePath().normalize();
    Path candidate = Path.of(storedPath).toAbsolutePath().normalize();
    if (!candidate.startsWith(output)) {
      throw new IOException("Invalid result path");
    }
    return candidate;
  }

  public Path root() {
    return root;
  }
}
