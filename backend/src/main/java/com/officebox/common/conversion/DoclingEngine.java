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

/** PDF to DOCX engine backed directly by the Docling document model. */
public final class DoclingEngine {
  private static final Duration TIMEOUT = Duration.ofMinutes(15);
  private final String python;
  private final Path bridge;

  public DoclingEngine() {
    this(
        System.getenv().getOrDefault("OFFICEBOX_DOCLING_PYTHON", "/opt/docling-venv/bin/python"),
        Path.of(System.getenv().getOrDefault("OFFICEBOX_DOCLING_BRIDGE", "/app/docling_docx_bridge.py")));
  }

  DoclingEngine(String python, Path bridge) {
    this.python = python;
    this.bridge = bridge;
  }

  public boolean isAvailable() {
    return Files.isRegularFile(bridge) && Files.isExecutable(Path.of(python));
  }

  public boolean convert(Path input, Path output) throws IOException, InterruptedException {
    if (!isAvailable()) return false;
    Path parent = output.toAbsolutePath().normalize().getParent();
    if (parent != null) Files.createDirectories(parent);
    run(List.of(python, bridge.toString(), input.toAbsolutePath().toString(), output.toAbsolutePath().toString()),
        "Docling PDF to DOCX conversion");
    return Files.isRegularFile(output) && Files.size(output) > 0;
  }

  private static void run(List<String> command, String operation) throws IOException, InterruptedException {
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    ByteArrayOutputStream logs = new ByteArrayOutputStream();
    Thread reader = new Thread(() -> drain(process.getInputStream(), logs), "officebox-docling-log");
    reader.setDaemon(true);
    reader.start();
    boolean finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      process.waitFor(5, TimeUnit.SECONDS);
      throw new IOException(operation + " timed out after " + TIMEOUT.toMinutes() + " minutes");
    }
    reader.join(TimeUnit.SECONDS.toMillis(5));
    if (process.exitValue() != 0) {
      String message = logs.toString(StandardCharsets.UTF_8).trim();
      throw new IOException(operation + " failed with exit code " + process.exitValue()
          + (message.isBlank() ? "" : ": " + message));
    }
  }

  private static void drain(InputStream input, ByteArrayOutputStream output) {
    try (input) { input.transferTo(output); } catch (IOException ignored) { }
  }
}
