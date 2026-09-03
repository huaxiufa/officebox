package com.officebox.common.conversion;

import com.officebox.common.conversion.model.PageModel;
import com.officebox.common.conversion.model.TextBlock;
import com.officebox.common.task.TaskProgress;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Coordinates PDF layout analysis and editable DOCX rendering. */
@Service
public class PdfToWordService {
  private final PdfPageParser pageParser;
  private final DocxRenderer renderer;
  private final DoclingEngine doclingEngine;
  private final Pdf2DocxEngine legacyEngine;

  @Autowired
  public PdfToWordService(PdfPageParser pageParser, DocxRenderer renderer) {
    this(pageParser, renderer, new DoclingEngine(), new Pdf2DocxEngine());
  }

  public PdfToWordService(PdfPageParser pageParser) {
    this(pageParser, new DocxRenderer(), new DoclingEngine(), new Pdf2DocxEngine());
  }

  PdfToWordService(PdfPageParser pageParser, Pdf2DocxEngine legacyEngine) {
    this(pageParser, new DocxRenderer(), new DoclingEngine(), legacyEngine);
  }

  PdfToWordService(PdfPageParser pageParser, DocxRenderer renderer, Pdf2DocxEngine legacyEngine) {
    this(pageParser, renderer, new DoclingEngine(), legacyEngine);
  }

  PdfToWordService(PdfPageParser pageParser, DocxRenderer renderer,
      DoclingEngine doclingEngine, Pdf2DocxEngine legacyEngine) {
    this.pageParser = pageParser;
    this.renderer = renderer;
    this.doclingEngine = doclingEngine;
    this.legacyEngine = legacyEngine;
  }

  public Path convert(Path input, Path outputDirectory, Consumer<TaskProgress> progress) throws IOException {
    validateInput(input, progress);
    Path outputRoot = outputDirectory.toAbsolutePath().normalize();
    Files.createDirectories(outputRoot);
    progress.accept(new TaskProgress(5, "Opening PDF"));

    String base = stripExtension(input.getFileName().toString());
    Path result = outputRoot.resolve(base + ".docx").normalize();
    if (!result.startsWith(outputRoot)) throw new IOException("Invalid output path");

    // Primary path: PDF coordinates -> editable DOCX. This avoids the
    // PDF -> HTML -> DOCX reflow that changes line wrapping and object positions.
    progress.accept(new TaskProgress(12, "Analyzing PDF layout"));
    List<PageModel> pages = new ArrayList<>();
    boolean hasSelectableText = false;
    try (PDDocument document = Loader.loadPDF(input.toFile())) {
      int total = document.getNumberOfPages();
      if (total == 0) throw new IOException("PDF contains no pages");
      for (int page = 1; page <= total; page++) {
        PageModel model = pageParser.parse(document, page);
        pages.add(model);
        hasSelectableText |= model.blocks().stream().anyMatch(TextBlock.class::isInstance);
        int percent = 12 + (int) Math.round(page * 48.0 / total);
        progress.accept(new TaskProgress(percent, "Analyzing PDF page " + page + " of " + total));
      }
    } catch (IOException | RuntimeException nativeParseFailure) {
      pages.clear();
      progress.accept(new TaskProgress(20, "Native layout analysis failed; trying fallback engine"));
    }

    if (!pages.isEmpty() && hasSelectableText) {
      try {
        progress.accept(new TaskProgress(65, "Building editable Word layout"));
        renderer.render(pages, result);
        if (isValidDocx(result)) {
          progress.accept(new TaskProgress(100, "PDF converted to editable DOCX"));
          return result;
        }
      } catch (IOException | RuntimeException nativeRenderFailure) {
        progress.accept(new TaskProgress(68, "Native rendering failed; trying fallback engine"));
      }
    } else if (!pages.isEmpty()) {
      progress.accept(new TaskProgress(20, "No selectable text; trying OCR engine"));
    }

    if (doclingEngine.isAvailable()) {
      progress.accept(new TaskProgress(25, "Converting with Docling OCR/layout engine"));
      try {
        if (doclingEngine.convert(input, result) && isValidDocx(result)) {
          progress.accept(new TaskProgress(100, "DOCX generated with Docling fallback"));
          return result;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("PDF to DOCX conversion interrupted", e);
      } catch (IOException | RuntimeException e) {
        progress.accept(new TaskProgress(30, "Docling failed; trying pdf2docx"));
      }
    }

    if (legacyEngine.isAvailable()) {
      progress.accept(new TaskProgress(35, "Converting with pdf2docx fallback"));
      try {
        if (legacyEngine.convert(input, result) && isValidDocx(result)) {
          progress.accept(new TaskProgress(100, "DOCX generated with pdf2docx fallback"));
          return result;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("PDF to DOCX conversion interrupted", e);
      } catch (IOException | RuntimeException e) {
        progress.accept(new TaskProgress(40, "pdf2docx failed; using final Java fallback"));
      }
    }

    if (pages.isEmpty()) {
      try (PDDocument document = Loader.loadPDF(input.toFile())) {
        int total = document.getNumberOfPages();
        if (total == 0) throw new IOException("PDF contains no pages");
        for (int page = 1; page <= total; page++) pages.add(pageParser.parse(document, page));
      }
    }
    progress.accept(new TaskProgress(85, "Rendering final editable DOCX"));
    renderer.render(pages, result);
    if (!isValidDocx(result)) throw new IOException("Conversion did not produce a valid DOCX result");
    progress.accept(new TaskProgress(100, "DOCX generated"));
    return result;
  }

  private static boolean isValidDocx(Path result) throws IOException {
    return Files.isRegularFile(result) && Files.size(result) > 0;
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
