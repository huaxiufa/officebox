package com.officebox.common.conversion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Optional high-fidelity PDF to DOCX engine backed by the open-source pdf2docx
 * project. The application falls back to the native Java renderer when the
 * Python engine is not installed (for example in unit-test environments).
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
    Files.createDirectories(output.toAbsolutePath().normalize().getParent());

    Process process = new ProcessBuilder(List.of(
        python, bridge.toString(), input.toAbsolutePath().toString(), output.toAbsolutePath().toString()))
        .redirectErrorStream(true)
        .start();

    String logs;
    try (var stream = process.getInputStream()) {
      logs = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    boolean finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      throw new IOException("PDF to DOCX conversion timed out after " + TIMEOUT.toMinutes() + " minutes");
    }
    if (process.exitValue() != 0) {
      throw new IOException("pdf2docx failed with exit code " + process.exitValue() + ": " + logs);
    }
    return Files.isRegularFile(output) && Files.size(output) > 0;
  }
}
