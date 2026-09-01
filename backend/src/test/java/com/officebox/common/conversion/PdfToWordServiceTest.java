package com.officebox.common.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.officebox.common.conversion.model.PageModel;
import com.officebox.common.task.TaskProgress;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfToWordServiceTest {
  @TempDir
  Path tempDir;

  @Test
  void rejectsMissingInput() {
    PdfToWordService service = new PdfToWordService(new StubPdfPageParser());
    assertThrows(IOException.class, () -> service.convert(
        tempDir.resolve("missing.pdf"), tempDir.resolve("output"), progress -> {}));
  }

  @Test
  void rejectsNonPdfInput() throws IOException {
    Path input = Files.createFile(tempDir.resolve("document.txt"));
    PdfToWordService service = new PdfToWordService(new StubPdfPageParser());
    assertThrows(IOException.class, () -> service.convert(
        input, tempDir.resolve("output"), progress -> {}));
  }

  @Test
  void requiresProgressReporter() throws IOException {
    Path input = Files.createFile(tempDir.resolve("document.pdf"));
    PdfToWordService service = new PdfToWordService(new StubPdfPageParser());
    assertThrows(IOException.class, () -> service.convert(
        input, tempDir.resolve("output"), null));
  }

  private static final class StubPdfPageParser extends PdfPageParser {
    @Override
    public PageModel parse(org.apache.pdfbox.pdmodel.PDDocument document, int pageNumber) {
      return new PageModel(pageNumber, 612, 792, List.of());
    }
  }
}
