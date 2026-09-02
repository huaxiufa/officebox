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

/**
 * High-fidelity PDF to DOCX engine backed by the open-source pdf2docx project.
 */
public final class Pdf2DocxEngine {
  private static final Duration TIMEOUT = Duration.ofMinutes(10);

  private final String python;
  private final Path bridge;

  public Pdf2DocxEngine() {
    this(
        System.getenv().getOrDefault("OFFICEBOX_PDF2DOCX_PYTHON", "/opt/pdf2docx-venv/bin/python"),
        Path.of(System.getenv().getOrDefault("OFFICEBOX_PDF2DOCX_BRIDGE", "/app/pdf2docx_bridge.py")));
  }

  Pdf2DocxEngine(String python, Path bridge) {
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

    Process process = new ProcessBuilder(List.of(
        python, bridge.toString(), input.toAbsolutePath().toString(), output.toAbsolutePath().toString()))
        .redirectErrorStream(true)
        .start();

    ByteArrayOutputStream logs = new ByteArrayOutputStream();
    Thread logReader = new Thread(() -> drain(process.getInputStream(), logs), "officebox-pdf2docx-log");
    logReader.setDaemon(true);
    logReader.start();

    boolean finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      process.waitFor(5, TimeUnit.SECONDS);
      throw new IOException("PDF to DOCX conversion timed out after " + TIMEOUT.toMinutes() + " minutes");
    }
    logReader.join(TimeUnit.SECONDS.toMillis(5));

    if (process.exitValue() != 0) {
      String message = logs.toString(StandardCharsets.UTF_8);
      throw new IOException("pdf2docx failed with exit code " + process.exitValue()
          + (message.isBlank() ? "" : ": " + message));
    }
    return Files.isRegularFile(output) && Files.size(output) > 0;
  }

  private static void drain(InputStream input, ByteArrayOutputStream output) {
    try (input) {
      input.transferTo(output);
    } catch (IOException ignored) {
      // The process exit code remains the authoritative conversion result.
    }
  }
}
