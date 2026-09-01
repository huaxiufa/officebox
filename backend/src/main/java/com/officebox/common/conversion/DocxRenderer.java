package com.officebox.common.conversion;

import com.officebox.common.conversion.model.PageModel;
import com.officebox.common.conversion.model.TextBlock;
import com.officebox.common.conversion.model.TextSpan;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

/** Renders the intermediate PDF model as editable DOCX content. */
public class DocxRenderer {
  private static final double PT_PER_TWIP = 1.0 / 20.0;

  public Path render(List<PageModel> pages, Path output) throws IOException {
    if (pages == null || pages.isEmpty()) {
      throw new IOException("No PDF pages to render");
    }
    Files.createDirectories(output.toAbsolutePath().normalize().getParent());
    try (XWPFDocument document = new XWPFDocument()) {
      for (int i = 0; i < pages.size(); i++) {
        renderPage(document, pages.get(i));
        if (i + 1 < pages.size()) {
          document.createParagraph().createRun().addBreak();
          document.createParagraph().getRuns().get(0).addBreak();
        }
      }
      try (OutputStream stream = Files.newOutputStream(output)) {
        document.write(stream);
      }
    }
    return output;
  }

  private void renderPage(XWPFDocument document, PageModel page) {
    CTSectPr section = document.getDocument().getBody().isSetSectPr()
        ? document.getDocument().getBody().getSectPr()
        : document.getDocument().getBody().addNewSectPr();
    CTPageSz pageSize = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
    pageSize.setW(toTwips(page.width()));
    pageSize.setH(toTwips(page.height()));
    CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
    margins.setTop(0);
    margins.setBottom(0);
    margins.setLeft(0);
    margins.setRight(0);

    double previousBottom = 0;
    for (TextBlock block : page.blocks()) {
      XWPFParagraph paragraph = document.createParagraph();
      double gap = Math.max(0, block.bounds().y() - previousBottom);
      if (gap > 0) {
        paragraph.setSpacingBefore((int) Math.round(gap * 20));
      }
      paragraph.setSpacingAfter(0);
      paragraph.setAlignment(ParagraphAlignment.LEFT);
      for (TextSpan span : block.spans()) {
        XWPFRun run = paragraph.createRun();
        run.setText(span.text());
        run.setFontSize(Math.max(1, span.fontSize()));
        String font = normalizeFont(span.fontName());
        if (!font.isBlank()) run.setFontFamily(font);
        run.setBold(span.bold());
        run.setItalic(span.italic());
        if (span.red() != 0 || span.green() != 0 || span.blue() != 0) {
          run.setColor(String.format("%02X%02X%02X", span.red(), span.green(), span.blue()));
        }
      }
      previousBottom = block.bounds().bottom();
    }
  }

  private static int toTwips(double points) {
    return (int) Math.round(points / PT_PER_TWIP);
  }

  private static String normalizeFont(String font) {
    if (font == null) return "";
    int plus = font.indexOf('+');
    return plus >= 0 && plus + 1 < font.length() ? font.substring(plus + 1) : font;
  }
}
