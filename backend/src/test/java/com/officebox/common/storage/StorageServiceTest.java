package com.officebox.common.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageServiceTest {
  @TempDir
  Path tempDir;

  @Test
  void resolvesRelativeOutputInsideOutputRoot() throws Exception {
    StorageService service = new StorageService(new StorageProperties(tempDir.toString(), 24));
    Path result = tempDir.resolve("output/task-1/result.docx");
    Files.createDirectories(result.getParent());
    Files.writeString(result, "docx");

    assertEquals(result.toAbsolutePath().normalize(), service.resolveOutput("task-1/result.docx"));
  }

  @Test
  void rejectsOutputPathTraversal() throws Exception {
    StorageService service = new StorageService(new StorageProperties(tempDir.toString(), 24));
    assertThrows(Exception.class, () -> service.resolveOutput("../tasks.json"));
  }

  @Test
  void relativizesOnlyFilesUnderOutput() throws Exception {
    StorageService service = new StorageService(new StorageProperties(tempDir.toString(), 24));
    Path result = tempDir.resolve("output/task-2/result.docx");
    assertEquals("task-2" + java.io.File.separator + "result.docx", service.relativizeOutput(result));
    assertThrows(Exception.class, () -> service.relativizeOutput(tempDir.resolve("input/a.pdf")));
  }
}
