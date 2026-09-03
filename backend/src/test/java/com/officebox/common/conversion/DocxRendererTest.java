package com.officebox.common.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.officebox.common.conversion.model.BoundingBox;
import com.officebox.common.conversion.model.PageBlock;
import com.officebox.common.conversion.model.PageModel;
import com.officebox.common.conversion.model.TextBlock;
import com.officebox.common.conversion.model.TextSpan;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class DocxRendererTest {
  @Test
  void rendersFormattedDocumentWithHeadingsAndBullets() throws Exception {
    TextSpan headingSpan = new TextSpan("WORK EXPERIENCE", new BoundingBox(40, 40, 180, 18), "Arial-Bold", 15, 0, 0, 0, true, false);
    TextSpan bulletSpan = new TextSpan("• Lead business development", new BoundingBox(40, 80, 220, 14), "Arial", 10, 0, 0, 0, false, false);
    List<PageBlock> blocks = List.of(
        new TextBlock(headingSpan.bounds(), List.of(headingSpan), true),
        new TextBlock(bulletSpan.bounds(), List.of(bulletSpan), false));
    PageModel page = new PageModel(1, 595, 842, blocks);
    Path output = Files.createTempFile("officebox-docx-", ".docx");

    new DocxRenderer().render(List.of(page), output);

    assertFalse(Files.size(output) == 0);
    try (XWPFDocument document = new XWPFDocument(Files.newInputStream(output))) {
      assertEquals("WORK EXPERIENCE", document.getParagraphs().get(0).getText());
      assertEquals("• Lead business development", document.getParagraphs().get(1).getText());
      assertFalse(document.getParagraphs().get(0).getRuns().isEmpty());
      assertFalse(document.getParagraphs().get(1).getRuns().isEmpty());
      assertEquals(16.5f, document.getParagraphs().get(0).getRuns().get(0).getFontSize(), 0.01f);
      assertEquals(10f, document.getParagraphs().get(1).getRuns().get(0).getFontSize(), 0.01f);
    } finally {
      Files.deleteIfExists(output);
    }
  }
}
