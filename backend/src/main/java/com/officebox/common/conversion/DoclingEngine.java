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
 * PDF -> DOCX conversion engine powered by Docling.
 *
 * Docling performs PDF understanding (text, reading order, tables, OCR and
 * images). LibreOffice is used only to turn Docling's editable HTML into DOCX.
 */
public final class DoclingEngine {
  private static final Duration DOCLING_TIMEOUT = Duration.ofMinutes(10);
  private static final Duration LIBREOFFICE_TIMEOUT = Duration.ofMinutes(3);

  private final String python;
  private final Path bridge;
  private final String soffice;

  public DoclingEngine() {
    this(
        System.getenv().getOrDefault("OFFICEBOX_DOCLING_PYTHON", "/opt/docling-venv/bin/python"),
        Path.of(System.getenv().getOrDefault("OFFICEBOX_DOCLING_BRIDGE", "/app/docling_bridge.py")),
        System.getenv().getOrDefault("OFFICEBOX_SOFFICE", "/usr/bin/libreoffice"));
  }

  DoclingEngine(String python, Path bridge, String soffice) {
    this.python = python;
    this.bridge = bridge;
    this.soffice = soffice;
  }

  public boolean isAvailable() {
    return Files.isRegularFile(bridge)
        && Files.isExecutable(Path.of(python))
        && commandAvailable(soffice);
  }

  public boolean convert(Path input, Path output) throws IOException, InterruptedException {
    if (!isAvailable()) return false;

    Path parent = output.toAbsolutePath().normalize().getParent();
    if (parent == null) throw new IOException("DOCX output directory is missing");
    Files.createDirectories(parent);

    Path workDir = Files.createTempDirectory(parent, ".docling-");
    Path html = workDir.resolve("document.html");
    Path generated = workDir.resolve("document.docx");
    try {
      run(
          List.of(python, bridge.toString(), input.toAbsolutePath().toString(), html.toString()),
          DOCLING_TIMEOUT,
          "Docling PDF analysis");

      if (!Files.isRegularFile(html) || Files.size(html) == 0) {
        throw new IOException("Docling produced no editable HTML");
      }

      run(
          List.of(
              soffice,
              "--headless",
              "--nologo",
              "--nodefault",
              "--nofirststartwizard",
              "-env:UserInstallation=" + workDir.resolve("lo-profile").toUri(),
              "--convert-to", "docx:Office Open XML Text",
              "--outdir", workDir.toString(),
              html.toString()),
          LIBREOFFICE_TIMEOUT,
          "HTML to DOCX rendering");

      if (!Files.isRegularFile(generated) || Files.size(generated) == 0) {
        throw new IOException("LibreOffice produced no editable DOCX");
      }
      Files.move(generated, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      return Files.isRegularFile(output) && Files.size(output) > 0;
    } finally {
      deleteRecursively(workDir);
    }
  }

  private static void run(List<String> command, Duration timeout, String operation)
      throws IOException, InterruptedException {
    Process process = new ProcessBuilder(command)
        .redirectErrorStream(true)
        .start();

    ByteArrayOutputStream logs = new ByteArrayOutputStream();
    Thread reader = new Thread(() -> drain(process.getInputStream(), logs), "officebox-docling-log");
    reader.setDaemon(true);
    reader.start();

    boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    if (!finished) {
      process.destroyForcibly();
      process.waitFor(5, TimeUnit.SECONDS);
      throw new IOException(operation + " timed out after " + timeout.toMinutes() + " minutes");
    }
    reader.join(TimeUnit.SECONDS.toMillis(5));

    if (process.exitValue() != 0) {
      String message = logs.toString(StandardCharsets.UTF_8).trim();
      if (message.length() > 4000) message = message.substring(message.length() - 4000);
      throw new IOException(operation + " failed with exit code " + process.exitValue()
          + (message.isBlank() ? "" : ": " + message));
    }
  }

  private static boolean commandAvailable(String command) {
    try {
      Process process = new ProcessBuilder(command, "--version")
          .redirectErrorStream(true)
          .start();
      return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static void drain(InputStream input, ByteArrayOutputStream output) {
    try (input) {
      input.transferTo(output);
    } catch (IOException ignored) {
      // Process exit code remains authoritative.
    }
  }

  private static void deleteRecursively(Path root) {
    if (root == null || !Files.exists(root)) return;
    try (var stream = Files.walk(root)) {
      stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (IOException ignored) {
          // Best-effort cleanup.
        }
      });
    } catch (IOException ignored) {
      // Best-effort cleanup.
    }
  }
}
