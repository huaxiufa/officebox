package com.officebox.common.conversion;

import com.officebox.common.conversion.model.PageBlock;
import com.officebox.common.conversion.model.PageModel;
import com.officebox.common.conversion.model.TextBlock;
import com.officebox.common.conversion.model.TextSpan;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Component;

/** Renders the intermediate PDF model as editable DOCX content. */
@Component
public class DocxRenderer {
  public Path render(List<PageModel> pages, Path output) throws IOException {
    if (pages == null || pages.isEmpty()) {
      throw new IOException("No PDF pages to render");
    }
    Path parent = output.toAbsolutePath().normalize().getParent();
    if (parent != null) Files.createDirectories(parent);
    try (XWPFDocument document = new XWPFDocument()) {
      for (int i = 0; i < pages.size(); i++) {
        renderPage(document, pages.get(i));
        if (i + 1 < pages.size()) {
          XWPFParagraph pageBreak = document.createParagraph();
          pageBreak.setPageBreak(true);
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
    pageSize.setW(BigInteger.valueOf(toTwips(page.width())));
    pageSize.setH(BigInteger.valueOf(toTwips(page.height())));
    CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
    margins.setTop(BigInteger.ZERO);
    margins.setBottom(BigInteger.ZERO);
    margins.setLeft(BigInteger.ZERO);
    margins.setRight(BigInteger.ZERO);

    double previousBottom = 0;
    for (PageBlock pageBlock : page.blocks()) {
      if (!(pageBlock instanceof TextBlock block)) continue;
      XWPFParagraph paragraph = document.createParagraph();
      double gap = Math.max(0, block.bounds().y() - previousBottom);
      if (gap > 0) paragraph.setSpacingBefore((int) Math.round(gap));
      paragraph.setSpacingAfter(0);
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
    return (int) Math.round(points * 20.0);
  }

  private static String normalizeFont(String font) {
    if (font == null) return "";
    int plus = font.indexOf('+');
    return plus >= 0 && plus + 1 < font.length() ? font.substring(plus + 1) : font;
  }
}
