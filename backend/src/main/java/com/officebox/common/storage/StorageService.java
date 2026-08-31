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
    Path inputDir = root.resolve("input").toAbsolutePath().normalize();
    Path target = inputDir.resolve(UUID.randomUUID() + "-" + original).normalize();
    if (!target.startsWith(inputDir)) {
      throw new IOException("Invalid file path");
    }
    try (InputStream input = file.getInputStream()) {
      Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
    }
    return target;
  }

  public Path resolveOutput(String storedPath) throws IOException {
    if (storedPath == null || storedPath.isBlank()) {
      throw new IOException("Result path is required");
    }
    Path output = root.resolve("output").toAbsolutePath().normalize();
    Path raw = Path.of(storedPath);
    Path candidate = raw.isAbsolute() ? raw.toAbsolutePath().normalize() : output.resolve(raw).normalize();
    if (!candidate.startsWith(output)) {
      throw new IOException("Invalid result path");
    }
    return candidate;
  }

  public String relativizeOutput(Path result) throws IOException {
    Path output = root.resolve("output").toAbsolutePath().normalize();
    Path candidate = result.toAbsolutePath().normalize();
    if (!candidate.startsWith(output)) {
      throw new IOException("Invalid result path");
    }
    return output.relativize(candidate).toString();
  }

  public void deleteInput(Path input) throws IOException {
    if (input == null) return;
    Path inputRoot = root.resolve("input").toAbsolutePath().normalize();
    Path candidate = input.toAbsolutePath().normalize();
    if (!candidate.startsWith(inputRoot)) {
      throw new IOException("Invalid input path");
    }
    Files.deleteIfExists(candidate);
  }

  public void deleteOutput(Path outputFile) throws IOException {
    if (outputFile == null) return;
    Path outputRoot = root.resolve("output").toAbsolutePath().normalize();
    Path candidate = outputFile.toAbsolutePath().normalize();
    if (!candidate.startsWith(outputRoot)) {
      throw new IOException("Invalid output path");
    }
    Files.deleteIfExists(candidate);
  }

  public Path root() {
    return root;
  }
}
