package com.officebox.common.conversion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** PDF to DOCX engine backed by Docling for layout-aware document understanding. */
public final class DoclingEngine {
  private static final Duration TIMEOUT = Duration.ofMinutes(15);

  private final String python;
  private final Path bridge;
  private final String libreOffice;

  public DoclingEngine() {
    this(
        System.getenv().getOrDefault("OFFICEBOX_DOCLING_PYTHON", "/opt/docling-venv/bin/python"),
        Path.of(System.getenv().getOrDefault("OFFICEBOX_DOCLING_BRIDGE", "/app/docling_bridge.py")),
        System.getenv().getOrDefault("OFFICEBOX_LIBREOFFICE", "/usr/bin/libreoffice"));
  }

  DoclingEngine(String python, Path bridge, String libreOffice) {
    this.python = python;
    this.bridge = bridge;
    this.libreOffice = libreOffice;
  }

  public boolean isAvailable() {
    return Files.isRegularFile(bridge)
        && Files.isExecutable(Path.of(python))
        && Files.isExecutable(Path.of(libreOffice));
  }

  public boolean convert(Path input, Path output) throws IOException, InterruptedException {
    if (!isAvailable()) return false;

    Path parent = output.toAbsolutePath().normalize().getParent();
    if (parent != null) Files.createDirectories(parent);

    Path workDir = Files.createTempDirectory(parent, ".docling-");
    Path html = workDir.resolve("document.html");
    try {
      run(List.of(python, bridge.toString(), input.toAbsolutePath().toString(), html.toString()),
          "Docling PDF analysis");

      Path generated = workDir.resolve("document.docx");
      run(List.of(
          libreOffice,
          "--headless",
          "--convert-to", "docx:Office Open XML Text",
          "--outdir", workDir.toString(),
          html.toString()),
          "HTML to DOCX rendering");

      if (!Files.isRegularFile(generated) || Files.size(generated) == 0) {
        throw new IOException("Docling produced no DOCX output");
      }
      Files.move(generated, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      return Files.isRegularFile(output) && Files.size(output) > 0;
    } finally {
      deleteRecursively(workDir);
    }
  }

  private static void run(List<String> command, String operation) throws IOException, InterruptedException {
    Process process = new ProcessBuilder(command)
        .redirectErrorStream(true)
        .start();

    ByteArrayOutputStream logs = new ByteArrayOutputStream();
    Thread logReader = new Thread(() -> drain(process.getInputStream(), logs), "officebox-docling-log");
    logReader.setDaemon(true);
    logReader.start();

    boolean finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      process.waitFor(5, TimeUnit.SECONDS);
      throw new IOException(operation + " timed out after " + TIMEOUT.toMinutes() + " minutes");
    }
    logReader.join(TimeUnit.SECONDS.toMillis(5));

    if (process.exitValue() != 0) {
      String message = logs.toString(StandardCharsets.UTF_8).trim();
      throw new IOException(operation + " failed with exit code " + process.exitValue()
          + (message.isBlank() ? "" : ": " + message));
    }
  }

  private static void drain(InputStream input, ByteArrayOutputStream output) {
    try (input) {
      input.transferTo(output);
    } catch (IOException ignored) {
      // The process exit code remains authoritative.
    }
  }

  private static void deleteRecursively(Path root) {
    if (root == null || !Files.exists(root)) return;
    try (var stream = Files.walk(root)) {
      stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (IOException ignored) {
          // Temporary files are best-effort cleanup only.
        }
      });
    } catch (IOException ignored) {
      // Best-effort cleanup.
    }
  }
}
