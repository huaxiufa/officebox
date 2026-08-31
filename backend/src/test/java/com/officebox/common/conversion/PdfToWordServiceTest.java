package com.officebox.common.conversion;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfToWordServiceTest {
  @TempDir
  Path tempDir;

  @Test
  void rejectsMissingInput() {
    PdfToWordService service = new PdfToWordService("soffice");
    assertThrows(IOException.class, () -> service.convert(
        tempDir.resolve("missing.pdf"), tempDir.resolve("output"), progress -> {}));
  }

  @Test
  void rejectsNonPdfInput() throws IOException {
    Path input = Files.createFile(tempDir.resolve("document.txt"));
    PdfToWordService service = new PdfToWordService("soffice");
    assertThrows(IOException.class, () -> service.convert(
        input, tempDir.resolve("output"), progress -> {}));
  }

  @Test
  void requiresProgressReporter() throws IOException {
    Path input = Files.createFile(tempDir.resolve("document.pdf"));
    PdfToWordService service = new PdfToWordService("soffice");
    assertThrows(IOException.class, () -> service.convert(
        input, tempDir.resolve("output"), null));
  }
}
