package com.officebox.common.conversion;

import com.officebox.common.conversion.model.BoundingBox;
import com.officebox.common.conversion.model.PageModel;
import com.officebox.common.conversion.model.TextBlock;
import com.officebox.common.conversion.model.TextSpan;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

/** Extracts PDF text into a coordinate-aware intermediate representation. */
@Component
public class PdfPageParser {
  public PageModel parse(PDDocument document, int pageNumber) throws IOException {
    PDPage page = document.getPage(pageNumber - 1);
    double width = page.getMediaBox().getWidth();
    double height = page.getMediaBox().getHeight();

    PositionStripper stripper = new PositionStripper();
    stripper.setSortByPosition(true);
    stripper.setStartPage(pageNumber);
    stripper.setEndPage(pageNumber);
    stripper.getText(document);

    List<TextBlock> blocks = new ArrayList<>();
    for (Line line : stripper.lines()) {
      if (line.glyphs.isEmpty()) continue;
      List<TextSpan> spans = new ArrayList<>();
      Span current = null;
      for (Glyph glyph : line.glyphs) {
        boolean bold = glyph.font.toLowerCase().contains("bold") || glyph.font.toLowerCase().contains("black");
        boolean italic = glyph.font.toLowerCase().contains("italic") || glyph.font.toLowerCase().contains("oblique");
        if (current == null || Math.abs(current.size - glyph.size) > .5
            || current.bold != bold || current.italic != italic
            || !current.font.equals(glyph.font)) {
          current = new Span(glyph.text, glyph.size, glyph.font, bold, italic, glyph.x, glyph.y,
              glyph.width, glyph.height);
          spans.add(current.toTextSpan());
        } else {
          int last = spans.size() - 1;
          TextSpan previous = spans.get(last);
          spans.set(last, new TextSpan(previous.text() + glyph.text(),
              new BoundingBox(previous.bounds().x(), previous.bounds().y(),
                  Math.max(previous.bounds().width(), glyph.x + glyph.width - previous.bounds().x()),
                  Math.max(previous.bounds().height(), glyph.y + glyph.height - previous.bounds().y())),
              previous.fontName(), previous.fontSize(), previous.red(), previous.green(), previous.blue(),
              previous.bold(), previous.italic()));
          current.width += glyph.width;
        }
      }
      double minX = spans.stream().mapToDouble(s -> s.bounds().x()).min().orElse(0);
      double minY = spans.stream().mapToDouble(s -> s.bounds().y()).min().orElse(0);
      double maxX = spans.stream().mapToDouble(s -> s.bounds().right()).max().orElse(minX);
      double maxY = spans.stream().mapToDouble(s -> s.bounds().bottom()).max().orElse(minY);
      double averageSize = spans.stream().mapToDouble(TextSpan::fontSize).average().orElse(10);
      boolean heading = averageSize >= 13 || (line.textLength() <= 48 && spans.stream().anyMatch(TextSpan::bold));
      blocks.add(new TextBlock(new BoundingBox(minX, minY, maxX - minX, maxY - minY), spans, heading));
    }
    blocks.sort(Comparator.comparingDouble((TextBlock b) -> b.bounds().y())
        .thenComparingDouble(b -> b.bounds().x()));
    return new PageModel(pageNumber, width, height, List.copyOf(blocks));
  }

  private static final class PositionStripper extends PDFTextStripper {
    private final List<Glyph> glyphs = new ArrayList<>();
    PositionStripper() throws IOException { super(); }
    @Override protected void writeString(String text, List<TextPosition> positions) {
      for (TextPosition p : positions) {
        String value = p.getUnicode();
        if (value == null || value.isEmpty()) continue;
        glyphs.add(new Glyph(value, p.getXDirAdj(), p.getYDirAdj(), p.getWidthDirAdj(),
            p.getHeightDir(), p.getFontSizeInPt(), p.getFont() == null ? "" : p.getFont().getName()));
      }
    }
    List<Line> lines() {
      glyphs.sort(Comparator.comparingDouble((Glyph g) -> g.y).thenComparingDouble(g -> g.x));
      List<Line> result = new ArrayList<>();
      for (Glyph glyph : glyphs) {
        Line target = null;
        for (int i = result.size() - 1; i >= 0; i--) {
          Line line = result.get(i);
          if (Math.abs(line.y - glyph.y) <= Math.max(2.5, glyph.size * .45)) { target = line; break; }
          if (line.y < glyph.y - 9) break;
        }
        if (target == null) { target = new Line(glyph.y); result.add(target); }
        target.glyphs.add(glyph);
      }
      result.sort(Comparator.comparingDouble(l -> l.y));
      for (Line line : result) line.glyphs.sort(Comparator.comparingDouble(g -> g.x));
      return result;
    }
  }

  private static final class Glyph {
    final String text, font; final double x, y, width, height, size;
    Glyph(String text, double x, double y, double width, double height, double size, String font) {
      this.text = text; this.x = x; this.y = y; this.width = width; this.height = height; this.size = size; this.font = font;
    }
  }
  private static final class Line {
    final double y; final List<Glyph> glyphs = new ArrayList<>();
    Line(double y) { this.y = y; }
    int textLength() { return glyphs.stream().mapToInt(g -> g.text.length()).sum(); }
  }
  private static final class Span {
    String text, font; double size, x, y, width, height; boolean bold, italic;
    Span(String text, double size, String font, boolean bold, boolean italic, double x, double y, double width, double height) {
      this.text = text; this.size = size; this.font = font; this.bold = bold; this.italic = italic;
      this.x = x; this.y = y; this.width = width; this.height = height;
    }
    TextSpan toTextSpan() { return new TextSpan(text, new BoundingBox(x, y, width, height), font, size, 0, 0, 0, bold, italic); }
  }
}
