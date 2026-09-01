package com.officebox.common.conversion;

import com.officebox.common.conversion.model.PageModel;
import com.officebox.common.task.TaskProgress;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

/** Coordinates PDF parsing and editable DOCX rendering. */
@Service
public class PdfToWordService {
  private final PdfPageParser pageParser;
  private final DocxRenderer renderer;

  public PdfToWordService(PdfPageParser pageParser) {
    this(pageParser, new DocxRenderer());
  }

  PdfToWordService(PdfPageParser pageParser, DocxRenderer renderer) {
    this.pageParser = pageParser;
    this.renderer = renderer;
  }

  public Path convert(Path input, Path outputDirectory, Consumer<TaskProgress> progress) throws IOException {
    validateInput(input, progress);
    Path outputRoot = outputDirectory.toAbsolutePath().normalize();
    Files.createDirectories(outputRoot);
    progress.accept(new TaskProgress(5, "Opening PDF"));

    List<PageModel> pages = new ArrayList<>();
    try (PDDocument document = Loader.loadPDF(input.toFile())) {
      int total = document.getNumberOfPages();
      if (total == 0) throw new IOException("PDF contains no pages");
      for (int page = 1; page <= total; page++) {
        pages.add(pageParser.parse(document, page));
        int percent = 10 + (int) Math.round(page * 65.0 / total);
        progress.accept(new TaskProgress(percent, "Analyzing PDF page " + page + " of " + total));
      }
    }

    String base = stripExtension(input.getFileName().toString());
    Path result = outputRoot.resolve(base + ".docx").normalize();
    if (!result.startsWith(outputRoot)) throw new IOException("Invalid output path");
    progress.accept(new TaskProgress(80, "Rendering editable DOCX"));
    renderer.render(pages, result);
    if (!Files.isRegularFile(result) || Files.size(result) == 0) {
      throw new IOException("Conversion did not produce a valid DOCX result");
    }
    progress.accept(new TaskProgress(95, "DOCX generated"));
    return result;
  }

  private static void validateInput(Path input, Consumer<TaskProgress> progress) throws IOException {
    if (input == null || !Files.isRegularFile(input)) throw new IOException("Input PDF does not exist");
    if (!input.getFileName().toString().toLowerCase().endsWith(".pdf")) throw new IOException("Input must be a PDF");
    if (progress == null) throw new IOException("Progress reporter is required");
  }

  private static String stripExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }
}
