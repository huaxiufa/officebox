package com.officebox.common.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
  void convertsAndReportsProgress() throws Exception {
    Path input = Files.createFile(tempDir.resolve("document.pdf"));
    Path fakeSoffice = tempDir.resolve("fake-soffice.sh");
    Files.writeString(fakeSoffice, "#!/bin/sh\nout=''\nprev=''\nfor arg in \"$@\"; do\n  if [ \"$prev\" = \"--outdir\" ]; then out=\"$arg\"; fi\n  prev=\"$arg\"\ndone\nprintf 'fake-docx' > \"$out/document.docx\"\n");
    assertEquals(true, fakeSoffice.toFile().setExecutable(true));

    List<TaskProgress> progress = new ArrayList<>();
    Path result = new PdfToWordService(fakeSoffice.toString()).convert(
        input, tempDir.resolve("output"), progress::add);

    assertEquals(tempDir.resolve("output/document.docx").toAbsolutePath().normalize(), result);
    assertEquals(List.of(10, 35, 95), progress.stream().map(TaskProgress::percent).toList());
    assertEquals("fake-docx", Files.readString(result));
  }

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
