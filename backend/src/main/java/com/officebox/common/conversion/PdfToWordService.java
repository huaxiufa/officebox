package com.officebox.common.conversion;

import com.officebox.common.task.TaskProgress;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class PdfToWordService {
  private static final long TIMEOUT_SECONDS = 120;
  private final String executable;

  public PdfToWordService() {
    this(System.getenv().getOrDefault("OFFICEBOX_SOFFICE", "soffice"));
  }

  PdfToWordService(String executable) {
    this.executable = executable;
  }

  public Path convert(Path input, Path outputDirectory, Consumer<TaskProgress> progress) throws IOException {
    if (input == null || !Files.isRegularFile(input)) {
      throw new IOException("Input PDF does not exist");
    }
    if (!input.getFileName().toString().toLowerCase().endsWith(".pdf")) {
      throw new IOException("Input must be a PDF");
    }
    if (progress == null) {
      throw new IOException("Progress reporter is required");
    }

    Path outputRoot = outputDirectory.toAbsolutePath().normalize();
    Files.createDirectories(outputRoot);
    progress.accept(new TaskProgress(10, "Preparing PDF conversion"));

    ProcessBuilder builder = new ProcessBuilder(List.of(
        executable, "--headless", "--convert-to", "docx", "--outdir",
        outputRoot.toString(), input.toAbsolutePath().toString()));
    builder.redirectErrorStream(true);
    builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
    Process process = builder.start();
    try {
      progress.accept(new TaskProgress(35, "Converting PDF to DOCX"));
      if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new IOException("PDF to Word conversion timed out");
      }
      if (process.exitValue() != 0) {
        throw new IOException("LibreOffice conversion failed with exit code " + process.exitValue());
      }
    } catch (InterruptedException e) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new IOException("PDF to Word conversion interrupted", e);
    }

    String base = stripExtension(input.getFileName().toString());
    Path result = outputRoot.resolve(base + ".docx").normalize();
    if (!result.startsWith(outputRoot) || !Files.isRegularFile(result) || Files.size(result) == 0) {
      throw new IOException("Conversion did not produce a valid DOCX result");
    }
    progress.accept(new TaskProgress(95, "DOCX generated"));
    return result;
  }

  private static String stripExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }
}
