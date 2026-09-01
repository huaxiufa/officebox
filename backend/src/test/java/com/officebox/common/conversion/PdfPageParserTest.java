package com.officebox.common.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.officebox.common.conversion.model.TextBlock;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfPageParserTest {
  @Test
  void extractsCoordinateAwareTextBlocks() throws Exception {
    Path pdf = Files.createTempFile("officebox-parser-", ".pdf");
    try {
      try (PDDocument document = new PDDocument()) {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
          stream.beginText();
          stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
          stream.newLineAtOffset(72, 700);
          stream.showText("OfficeBox");
          stream.endText();
          stream.beginText();
          stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
          stream.newLineAtOffset(72, 660);
          stream.showText("Editable document conversion");
          stream.endText();
        }
        document.save(pdf.toFile());
      }

      try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
        var model = new PdfPageParser().parse(document, 1);
        assertEquals(1, model.pageNumber());
        assertEquals(2, model.blocks().size());

        TextBlock heading = (TextBlock) model.blocks().get(0);
        TextBlock body = (TextBlock) model.blocks().get(1);
        assertEquals("OfficeBox", heading.text());
        assertTrue(heading.heading());
        assertEquals("Editable document conversion", body.text());
        assertTrue(heading.bounds().y() < body.bounds().y());
      }
    } finally {
      Files.deleteIfExists(pdf);
    }
  }
}
