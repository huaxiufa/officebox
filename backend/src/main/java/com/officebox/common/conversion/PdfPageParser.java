package com.officebox.common.conversion;

import com.officebox.common.conversion.model.BoundingBox;
import com.officebox.common.conversion.model.ImageBlock;
import com.officebox.common.conversion.model.PageBlock;
import com.officebox.common.conversion.model.PageModel;
import com.officebox.common.conversion.model.TextBlock;
import com.officebox.common.conversion.model.TextSpan;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Extracts PDF content into a coordinate-aware intermediate representation. */
@Component
public class PdfPageParser {
  private static final float ARTWORK_DPI = 144f;
  private final PdfLayoutAnalyzer layoutAnalyzer;

  public PdfPageParser() { this(new PdfLayoutAnalyzer()); }
  @Autowired public PdfPageParser(PdfLayoutAnalyzer layoutAnalyzer) { this.layoutAnalyzer = layoutAnalyzer; }

  public PageModel parse(PDDocument document, int pageNumber) throws IOException {
    PDPage page = document.getPage(pageNumber - 1);
    double width = page.getMediaBox().getWidth(), height = page.getMediaBox().getHeight();
    PositionStripper stripper = new PositionStripper();
    stripper.setSortByPosition(true); stripper.setStartPage(pageNumber); stripper.setEndPage(pageNumber); stripper.getText(document);
    List<PageBlock> blocks = new ArrayList<>();
    for (Line line : stripper.lines()) {
      if (line.glyphs.isEmpty()) continue;
      List<TextSpan> spans = new ArrayList<>(); Span current = null; Glyph previous = null;
      for (Glyph glyph : line.glyphs) {
        boolean bold = glyph.font.toLowerCase().contains("bold") || glyph.font.toLowerCase().contains("black");
        boolean italic = glyph.font.toLowerCase().contains("italic") || glyph.font.toLowerCase().contains("oblique");
        boolean needsSpace = previous != null && !previous.text.isBlank() && !glyph.text.isBlank() && glyph.x - previous.right() > Math.max(1.2, glyph.size * .22);
        if (needsSpace && !spans.isEmpty()) { int last = spans.size() - 1; TextSpan old = spans.get(last); spans.set(last, new TextSpan(old.text() + " ", old.bounds(), old.fontName(), old.fontSize(), old.red(), old.green(), old.blue(), old.bold(), old.italic())); }
        if (current == null || Math.abs(current.size - glyph.size) > .5 || current.bold != bold || current.italic != italic || !current.font.equals(glyph.font)) {
          current = new Span(glyph.text, glyph.size, glyph.font, bold, italic, glyph.x, glyph.y, glyph.width, glyph.height); spans.add(current.toTextSpan());
        } else {
          int last = spans.size() - 1; TextSpan old = spans.get(last);
          spans.set(last, new TextSpan(old.text() + glyph.text, new BoundingBox(old.bounds().x(), old.bounds().y(), Math.max(old.bounds().width(), glyph.right() - old.bounds().x()), Math.max(old.bounds().height(), glyph.y + glyph.height - old.bounds().y())), old.fontName(), old.fontSize(), old.red(), old.green(), old.blue(), old.bold(), old.italic()));
          current.width = Math.max(current.width, glyph.right() - current.x); current.height = Math.max(current.height, glyph.y + glyph.height - current.y);
        }
        previous = glyph;
      }
      double minX = spans.stream().mapToDouble(s -> s.bounds().x()).min().orElse(0), minY = spans.stream().mapToDouble(s -> s.bounds().y()).min().orElse(0);
      double maxX = spans.stream().mapToDouble(s -> s.bounds().right()).max().orElse(minX), maxY = spans.stream().mapToDouble(s -> s.bounds().bottom()).max().orElse(minY);
      double averageSize = spans.stream().mapToDouble(TextSpan::fontSize).average().orElse(10);
      boolean heading = averageSize >= 13 || (line.textLength() <= 48 && spans.stream().anyMatch(TextSpan::bold));
      blocks.add(new TextBlock(new BoundingBox(minX, minY, maxX - minX, maxY - minY), spans, heading));
    }
    ImageExtractor extractor = new ImageExtractor(width, height); extractor.processPage(page); blocks.addAll(extractor.images());
    if (blocks.isEmpty()) blocks.add(renderPageArtwork(document, pageNumber, width, height));
    return new PageModel(pageNumber, width, height, layoutAnalyzer.analyze(blocks));
  }

  private static ImageBlock renderPageArtwork(PDDocument document, int pageNumber, double width, double height) throws IOException {
    BufferedImage image = new PDFRenderer(document).renderImageWithDPI(pageNumber - 1, ARTWORK_DPI, ImageType.RGB); ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out);
    return new ImageBlock(new BoundingBox(0, 0, width, height), "image/png", out.toByteArray());
  }

  private static final class ImageExtractor extends PDFStreamEngine {
    private final double pageWidth, pageHeight; private final List<ImageBlock> images = new ArrayList<>();
    ImageExtractor(double pageWidth, double pageHeight) { super(); this.pageWidth = pageWidth; this.pageHeight = pageHeight; }
    @Override protected void processOperator(org.apache.pdfbox.contentstream.operator.Operator operator, List<COSBase> operands) throws IOException {
      if ("Do".equals(operator.getName()) && !operands.isEmpty() && operands.get(0) instanceof COSName name) { PDXObject xObject = getResources().getXObject(name); if (xObject instanceof PDImageXObject image) addImage(image); }
      super.processOperator(operator, operands);
    }
    private void addImage(PDImageXObject image) throws IOException {
      Matrix matrix = getGraphicsState().getCurrentTransformationMatrix(); Point2D.Float p0 = matrix.transformPoint(0, 0), p1 = matrix.transformPoint(1, 0), p2 = matrix.transformPoint(0, 1), p3 = matrix.transformPoint(1, 1);
      double minX = Math.max(0, Math.min(Math.min(p0.x, p1.x), Math.min(p2.x, p3.x))), maxX = Math.min(pageWidth, Math.max(Math.max(p0.x, p1.x), Math.max(p2.x, p3.x)));
      double minPdfY = Math.max(0, Math.min(Math.min(p0.y, p1.y), Math.min(p2.y, p3.y))), maxPdfY = Math.min(pageHeight, Math.max(Math.max(p0.y, p1.y), Math.max(p2.y, p3.y)));
      double w = maxX - minX, h = maxPdfY - minPdfY; if (w < 2 || h < 2) return;
      String suffix = image.getSuffix(), format = "png", mime = "image/png";
      if ("jpg".equalsIgnoreCase(suffix) || "jpeg".equalsIgnoreCase(suffix)) { format = "jpg"; mime = "image/jpeg"; } else if ("gif".equalsIgnoreCase(suffix)) { format = "gif"; mime = "image/gif"; } else if ("bmp".equalsIgnoreCase(suffix)) { format = "bmp"; mime = "image/bmp"; }
      ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image.getImage(), format, out); images.add(new ImageBlock(new BoundingBox(minX, pageHeight - maxPdfY, w, h), mime, out.toByteArray()));
    }
    List<ImageBlock> images() { return List.copyOf(images); }
  }

  private static final class PositionStripper extends PDFTextStripper {
    private final List<Glyph> glyphs = new ArrayList<>(); PositionStripper() throws IOException { super(); }
    @Override protected void writeString(String text, List<TextPosition> positions) { for (TextPosition p : positions) { String value = p.getUnicode(); if (value != null && !value.isEmpty()) glyphs.add(new Glyph(value, p.getXDirAdj(), p.getYDirAdj(), p.getWidthDirAdj(), p.getHeightDir(), p.getFontSizeInPt(), p.getFont() == null ? "" : p.getFont().getName())); } }
    List<Line> lines() {
      glyphs.sort(Comparator.comparingDouble((Glyph g) -> g.y).thenComparingDouble(g -> g.x)); List<Line> result = new ArrayList<>();
      for (Glyph glyph : glyphs) { Line target = null; for (int i = result.size() - 1; i >= 0; i--) { Line line = result.get(i); if (Math.abs(line.y - glyph.y) <= Math.max(2.5, glyph.size * .45)) { target = line; break; } if (line.y < glyph.y - 9) break; } if (target == null) { target = new Line(glyph.y); result.add(target); } target.glyphs.add(glyph); }
      result.sort(Comparator.comparingDouble(l -> l.y)); for (Line line : result) line.glyphs.sort(Comparator.comparingDouble(g -> g.x)); return result;
    }
  }
  private static final class Glyph {
    final String text, font; final double x, y, width, height, size;
    Glyph(String text, double x, double y, double width, double height, double size, String font) { this.text=text; this.x=x; this.y=y; this.width=width; this.height=height; this.size=size; this.font=font; }
    double right() { return x + width; }
  }
  private static final class Line {
    final double y; final List<Glyph> glyphs = new ArrayList<>(); Line(double y){this.y=y;} int textLength(){return glyphs.stream().mapToInt(g->g.text.length()).sum();}
  }
  private static final class Span {
    String text,font; double size,x,y,width,height; boolean bold,italic;
    Span(String text,double size,String font,boolean bold,boolean italic,double x,double y,double width,double height){this.text=text;this.size=size;this.font=font;this.bold=bold;this.italic=italic;this.x=x;this.y=y;this.width=width;this.height=height;}
    TextSpan toTextSpan(){return new TextSpan(text,new BoundingBox(x,y,width,height),font,size,0,0,0,bold,italic);}
  }
}
