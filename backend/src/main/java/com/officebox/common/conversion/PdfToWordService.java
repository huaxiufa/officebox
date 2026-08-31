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
    this.executable = System.getenv().getOrDefault("OFFICEBOX_SOFFICE", "soffice");
  }

  public Path convert(Path input, Path outputDirectory, Consumer<TaskProgress> progress) throws IOException {
    if (!Files.isRegularFile(input)) throw new IOException("Input PDF does not exist");
    if (!input.getFileName().toString().toLowerCase().endsWith(".pdf")) throw new IOException("Input must be a PDF");
    Files.createDirectories(outputDirectory);
    progress.accept(new TaskProgress(10, "Preparing PDF conversion"));

    Process process = new ProcessBuilder(List.of(
        executable, "--headless", "--convert-to", "docx", "--outdir", outputDirectory.toString(), input.toAbsolutePath().toString()))
        .redirectErrorStream(true)
        .start();
    try {
      progress.accept(new TaskProgress(35, "Converting PDF to DOCX"));
      if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new IOException("PDF to Word conversion timed out");
      }
      String output = new String(process.getInputStream().readAllBytes());
      if (process.exitValue() != 0) throw new IOException("LibreOffice conversion failed: " + output.trim());
    } catch (InterruptedException e) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new IOException("PDF to Word conversion interrupted", e);
    }

    String base = stripExtension(input.getFileName().toString());
    Path result = outputDirectory.resolve(base + ".docx").normalize();
    if (!result.startsWith(outputDirectory.toAbsolutePath().normalize()) || !Files.isRegularFile(result)) {
      throw new IOException("Conversion did not produce a DOCX result");
    }
    progress.accept(new TaskProgress(95, "DOCX generated"));
    return result;
  }

  private static String stripExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }
}
