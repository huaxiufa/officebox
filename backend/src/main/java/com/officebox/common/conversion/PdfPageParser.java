package com.officebox.common.conversion;

import com.officebox.common.conversion.model.BoundingBox;
import com.officebox.common.conversion.model.ImageBlock;
import com.officebox.common.conversion.model.PageBlock;
import com.officebox.common.conversion.model.PageModel;
import com.officebox.common.conversion.model.TextBlock;
import com.officebox.common.conversion.model.TextSpan;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

/** Extracts PDF text and raster artwork into a coordinate-aware intermediate representation. */
public class PdfPageParser {
  private static final float ARTWORK_DPI = 144f;

  public PageModel parse(PDDocument document, int pageNumber) throws IOException {
    PDPage page = document.getPage(pageNumber - 1);
    double width = page.getMediaBox().getWidth();
    double height = page.getMediaBox().getHeight();

    PositionStripper stripper = new PositionStripper();
    stripper.setSortByPosition(true);
    stripper.setStartPage(pageNumber);
    stripper.setEndPage(pageNumber);
    stripper.getText(document);

    List<PageBlock> blocks = new ArrayList<>();
    for (Line line : stripper.lines()) {
      if (line.glyphs.isEmpty()) continue;
      List<TextSpan> spans = new ArrayList<>();
      Span current = null;
      for (Glyph glyph : line.glyphs) {
        boolean bold = glyph.font.toLowerCase().contains("bold") || glyph.font.toLowerCase().contains("black");
        boolean italic = glyph.font.toLowerCase().contains("italic") || glyph.font.toLowerCase().contains("oblique");
        if (current == null || Math.abs(current.size - glyph.size) > .5
            || current.bold != bold || current.italic != italic || !current.font.equals(glyph.font)) {
          current = new Span(glyph.text, glyph.size, glyph.font, bold, italic, glyph.x, glyph.y,
              glyph.width, glyph.height);
          spans.add(current.toTextSpan());
        } else {
          int last = spans.size() - 1;
          TextSpan previous = spans.get(last);
          spans.set(last, new TextSpan(previous.text() + glyph.text,
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

    // Preserve native raster images with their PDF coordinates. This is important for photos,
    // logos and signatures, while keeping the text layer editable.
    ImageExtractor extractor = new ImageExtractor(width, height);
    extractor.processPage(page);
    blocks.addAll(extractor.images());

    // A page with no extractable content is most safely represented by a visual fallback.
    // Text-bearing pages intentionally avoid this full-page image so it cannot cover editable text.
    if (blocks.isEmpty()) {
      blocks.add(renderPageArtwork(document, pageNumber, width, height));
    }

    blocks.sort(Comparator.comparingDouble((PageBlock b) -> b.bounds().y())
        .thenComparingDouble(b -> b.bounds().x()));
    return new PageModel(pageNumber, width, height, List.copyOf(blocks));
  }

  private static ImageBlock renderPageArtwork(PDDocument document, int pageNumber, double width, double height)
      throws IOException {
    PDFRenderer renderer = new PDFRenderer(document);
    BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, ARTWORK_DPI, ImageType.RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return new ImageBlock(new BoundingBox(0, 0, width, height), "image/png", out.toByteArray());
  }

  private static final class ImageExtractor extends PDFStreamEngine {
    private final double pageWidth;
    private final double pageHeight;
    private final List<ImageBlock> images = new ArrayList<>();

    ImageExtractor(double pageWidth, double pageHeight) throws IOException {
      super();
      this.pageWidth = pageWidth;
      this.pageHeight = pageHeight;
      addOperator(new DrawObject());
    }

    @Override
    protected void processOperator(org.apache.pdfbox.contentstream.operator.Operator operator,
                                   List<COSBase> operands) throws IOException {
      if (COSName.DRAW_OBJECT.getName().equals(operator.getName()) && !operands.isEmpty()
          && operands.get(0) instanceof COSName name) {
        PDXObject xObject = getResources().getXObject(name);
        if (xObject instanceof PDImageXObject image) addImage(image);
      }
      super.processOperator(operator, operands);
    }

    private void addImage(PDImageXObject image) throws IOException {
      Matrix matrix = getGraphicsState().getCurrentTransformationMatrix();
      float[] p0 = matrix.transformPoint(0, 0);
      float[] p1 = matrix.transformPoint(1, 0);
      float[] p2 = matrix.transformPoint(0, 1);
      float[] p3 = matrix.transformPoint(1, 1);
      double minX = Math.max(0, Math.min(Math.min(p0[0], p1[0]), Math.min(p2[0], p3[0])));
      double maxX = Math.min(pageWidth, Math.max(Math.max(p0[0], p1[0]), Math.max(p2[0], p3[0])));
      double minPdfY = Math.max(0, Math.min(Math.min(p0[1], p1[1]), Math.min(p2[1], p3[1])));
      double maxPdfY = Math.min(pageHeight, Math.max(Math.max(p0[1], p1[1]), Math.max(p2[1], p3[1])));
      double y = pageHeight - maxPdfY;
      double w = maxX - minX;
      double h = maxPdfY - minPdfY;
      if (w < 2 || h < 2) return;
      BufferedImage buffered = image.getImage();
      String suffix = image.getSuffix();
      String format = "png";
      String mime = "image/png";
      if ("jpg".equalsIgnoreCase(suffix) || "jpeg".equalsIgnoreCase(suffix)) { format = "jpg"; mime = "image/jpeg"; }
      else if ("gif".equalsIgnoreCase(suffix)) { format = "gif"; mime = "image/gif"; }
      else if ("bmp".equalsIgnoreCase(suffix)) { format = "bmp"; mime = "image/bmp"; }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(buffered, format, out);
      images.add(new ImageBlock(new BoundingBox(minX, y, w, h), mime, out.toByteArray()));
    }
    List<ImageBlock> images() { return List.copyOf(images); }
  }

  private static final class PositionStripper extends PDFTextStripper {
    private final List<Glyph> glyphs = new ArrayList<>();
    PositionStripper() throws IOException { super(); }
    @Override protected void writeString(String text, List<TextPosition> positions) {
      for (TextPosition p : positions) {
        String value = p.getUnicode();
        if (value == null || value.isEmpty()) continue;
        glyphs.add(new Glyph(value, p.getXDirAdj(), p.getYDirAdj(), p.getWidthDirAdj(), p.getHeightDir(),
            p.getFontSizeInPt(), p.getFont() == null ? "" : p.getFont().getName()));
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
