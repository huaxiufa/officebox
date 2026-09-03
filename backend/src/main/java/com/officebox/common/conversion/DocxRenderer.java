package com.officebox.common.conversion;

import com.officebox.common.conversion.model.ImageBlock;
import com.officebox.common.conversion.model.PageBlock;
import com.officebox.common.conversion.model.PageModel;
import com.officebox.common.conversion.model.TableBlock;
import com.officebox.common.conversion.model.TextBlock;
import com.officebox.common.conversion.model.TextSpan;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.springframework.stereotype.Component;

/** Renders the coordinate-aware PDF model as editable DOCX content. */
@Component
public class DocxRenderer {
  public Path render(List<PageModel> pages, Path output) throws IOException {
    if (pages == null || pages.isEmpty()) throw new IOException("No PDF pages to render");
    Path parent = output.toAbsolutePath().normalize().getParent();
    if (parent != null) Files.createDirectories(parent);
    try (XWPFDocument document = new XWPFDocument()) {
      configurePage(document, pages.getFirst());
      for (int i = 0; i < pages.size(); i++) {
        renderPage(document, pages.get(i));
        if (i + 1 < pages.size()) document.createParagraph().setPageBreak(true);
      }
      try (OutputStream stream = Files.newOutputStream(output)) { document.write(stream); }
    }
    return output;
  }

  private void configurePage(XWPFDocument document, PageModel page) {
    CTSectPr section = document.getDocument().getBody().isSetSectPr()
        ? document.getDocument().getBody().getSectPr() : document.getDocument().getBody().addNewSectPr();
    CTPageSz size = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
    size.setW(BigInteger.valueOf(twips(page.width()))); size.setH(BigInteger.valueOf(twips(page.height())));
    CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
    margins.setTop(BigInteger.ZERO); margins.setBottom(BigInteger.ZERO); margins.setLeft(BigInteger.ZERO); margins.setRight(BigInteger.ZERO);
  }

  private void renderPage(XWPFDocument document, PageModel page) {
    List<PageBlock> blocks = page.blocks().stream()
        .filter(block -> !(block instanceof ImageBlock image && isFullPageImage(image, page))).toList();
    if (blocks.isEmpty()) return;
    ColumnLayout columns = detectColumns(blocks, page.width());
    if (columns.twoColumns) renderTwoColumns(document, blocks, columns.split, page.width());
    else renderSingleColumn(document, blocks);
  }

  private void renderSingleColumn(XWPFDocument document, List<PageBlock> blocks) {
    double previousBottom = 0;
    for (PageBlock block : sorted(blocks)) {
      XWPFParagraph paragraph = document.createParagraph();
      positionParagraph(paragraph, block, previousBottom, 0);
      if (block instanceof TableBlock table) writeTableAfter(paragraph, table);
      else writeBlock(paragraph, block);
      previousBottom = Math.max(previousBottom, block.bounds().bottom());
    }
  }

  private void renderTwoColumns(XWPFDocument document, List<PageBlock> blocks, double split, double pageWidth) {
    List<PageBlock> left = new ArrayList<>(), right = new ArrayList<>();
    for (PageBlock block : blocks) {
      double center = block.bounds().x() + block.bounds().width() / 2;
      if (center < split) left.add(block); else right.add(block);
    }
    XWPFTable table = document.createTable(1, 2);
    table.setWidth("100%"); table.getCTTbl().getTblPr().unsetTblBorders();
    CTTblLayoutType layout = table.getCTTbl().getTblPr().isSetTblLayout()
        ? table.getCTTbl().getTblPr().getTblLayout() : table.getCTTbl().getTblPr().addNewTblLayout();
    layout.setType(STTblLayoutType.FIXED);
    XWPFTableRow row = table.getRow(0);
    XWPFTableCell leftCell = row.getCell(0), rightCell = row.getCell(1);
    setCellWidth(leftCell, split); setCellWidth(rightCell, pageWidth - split);
    clearCell(leftCell); clearCell(rightCell);
    renderCell(leftCell, left, 0); renderCell(rightCell, right, split);
  }

  private void renderCell(XWPFTableCell cell, List<PageBlock> blocks, double originX) {
    double previousBottom = 0;
    for (PageBlock block : sorted(blocks)) {
      if (block instanceof TableBlock nested) {
        XWPFParagraph anchor = cell.addParagraph();
        positionParagraph(anchor, block, previousBottom, originX);
        // Nested tables are rare; render their cells as paragraphs rather than losing content.
        for (List<TextBlock> row : nested.rows()) for (TextBlock text : row) {
          XWPFParagraph p = cell.addParagraph(); writeSpans(p, text.spans());
        }
      } else {
        XWPFParagraph paragraph = cell.addParagraph();
        positionParagraph(paragraph, block, previousBottom, originX);
        writeBlock(paragraph, block);
      }
      previousBottom = Math.max(previousBottom, block.bounds().bottom());
    }
  }

  private void writeBlock(XWPFParagraph paragraph, PageBlock block) {
    if (block instanceof TextBlock text) {
      paragraph.setKeepNext(text.heading());
      writeSpans(paragraph, text.spans());
    } else if (block instanceof ImageBlock image) {
      writeImage(paragraph, image);
    }
  }

  private void writeTableAfter(XWPFParagraph anchor, TableBlock table) {
    XWPFDocument document = anchor.getDocument();
    XmlCursor cursor = anchor.getCTP().newCursor();
    try {
      cursor.toEndToken();
      XWPFTable wordTable = document.insertNewTbl(cursor);
      wordTable.setWidth("100%");
      CTTblLayoutType layout = wordTable.getCTTbl().getTblPr().isSetTblLayout()
          ? wordTable.getCTTbl().getTblPr().getTblLayout() : wordTable.getCTTbl().getTblPr().addNewTblLayout();
      layout.setType(STTblLayoutType.FIXED);
      wordTable.getCTTbl().getTblPr().unsetTblBorders();
      // insertNewTbl creates a one-row/one-cell table; resize it to the detected grid.
      int targetRows = table.rows().size();
      int targetCols = table.rows().stream().mapToInt(List::size).max().orElse(1);
      while (wordTable.getNumberOfRows() < targetRows) wordTable.addRow();
      while (wordTable.getRow(0).getTableCells().size() < targetCols) wordTable.getRow(0).addNewTableCell();
      for (int r = 0; r < targetRows; r++) {
        XWPFTableRow row = wordTable.getRow(r);
        while (row.getTableCells().size() < targetCols) row.addNewTableCell();
        List<TextBlock> sourceRow = table.rows().get(r);
        for (int c = 0; c < sourceRow.size(); c++) {
          XWPFTableCell cell = row.getCell(c);
          clearCell(cell);
          XWPFParagraph p = cell.addParagraph(); p.setSpacingBefore(0); p.setSpacingAfter(0);
          writeSpans(p, sourceRow.get(c).spans());
        }
      }
    } finally {
      cursor.dispose();
    }
    anchor.setSpacingAfter(twips(1));
  }

  private static void positionParagraph(XWPFParagraph paragraph, PageBlock block, double previousBottom, double originX) {
    paragraph.setSpacingBefore(twips(Math.max(0, block.bounds().y() - previousBottom)));
    paragraph.setSpacingAfter(0);
    paragraph.setIndentationLeft(twips(Math.max(0, block.bounds().x() - originX)));
  }

  private static void writeImage(XWPFParagraph paragraph, ImageBlock image) {
    try {
      XWPFRun run = paragraph.createRun();
      run.addPicture(new ByteArrayInputStream(image.data()), pictureType(image.mimeType()), "pdf-image." + extension(image.mimeType()),
          Math.max(1, Units.toEMU(image.bounds().width())), Math.max(1, Units.toEMU(image.bounds().height())));
    } catch (Exception e) { throw new IllegalStateException("Unable to embed PDF image", e); }
  }

  private static void writeSpans(XWPFParagraph paragraph, List<TextSpan> spans) {
    for (TextSpan span : spans) {
      XWPFRun run = paragraph.createRun(); run.setText(span.text()); run.setFontSize((float) Math.max(1, span.fontSize()));
      String font = normalizeFont(span.fontName()); if (!font.isBlank()) run.setFontFamily(font);
      run.setBold(span.bold()); run.setItalic(span.italic());
      if (span.red() != 0 || span.green() != 0 || span.blue() != 0) run.setColor(String.format("%02X%02X%02X", span.red(), span.green(), span.blue()));
    }
  }

  private static ColumnLayout detectColumns(List<PageBlock> blocks, double pageWidth) {
    List<TextBlock> text = blocks.stream().filter(TextBlock.class::isInstance).map(TextBlock.class::cast).toList();
    if (text.size() < 8) return new ColumnLayout(false, pageWidth);
    List<Double> xs = text.stream().map(b -> b.bounds().x()).sorted().toList();
    double bestGap = 0, split = pageWidth;
    for (int i = 1; i < xs.size(); i++) {
      double a = xs.get(i - 1), b = xs.get(i), gap = b - a;
      if (a > pageWidth * .15 && b < pageWidth * .85 && gap > bestGap) { bestGap = gap; split = (a + b) / 2; }
    }
    boolean two = bestGap >= Math.max(32, pageWidth * .09);
    if (two) {
      long left = text.stream().filter(b -> b.bounds().x() < split).count();
      long right = text.size() - left;
      two = left >= 3 && right >= 3;
    }
    return new ColumnLayout(two, split);
  }

  private static List<PageBlock> sorted(List<PageBlock> blocks) {
    return blocks.stream().sorted(Comparator.comparingDouble((PageBlock b) -> b.bounds().y()).thenComparingDouble(b -> b.bounds().x())).toList();
  }
  private static boolean isFullPageImage(ImageBlock image, PageModel page) {
    return image.bounds().x() <= .5 && image.bounds().y() <= .5 && image.bounds().width() >= page.width() * .95 && image.bounds().height() >= page.height() * .95;
  }
  private static void clearCell(XWPFTableCell cell) { while (!cell.getParagraphs().isEmpty()) cell.removeParagraph(0); }
  private static void setCellWidth(XWPFTableCell cell, double points) { cell.setWidth(String.format("%.0fpt", Math.max(1, points))); }
  private static int twips(double points) { return (int) Math.round(points * 20); }
  private static String normalizeFont(String font) { if (font == null) return ""; int plus = font.indexOf('+'); return plus >= 0 && plus + 1 < font.length() ? font.substring(plus + 1) : font; }
  private static int pictureType(String mime) {
    if (mime == null) return Document.PICTURE_TYPE_PNG;
    return switch (mime.toLowerCase()) { case "image/jpeg", "image/jpg" -> Document.PICTURE_TYPE_JPEG; case "image/gif" -> Document.PICTURE_TYPE_GIF; case "image/bmp" -> Document.PICTURE_TYPE_BMP; case "image/emf" -> Document.PICTURE_TYPE_EMF; case "image/wmf" -> Document.PICTURE_TYPE_WMF; default -> Document.PICTURE_TYPE_PNG; };
  }
  private static String extension(String mime) {
    if (mime == null) return "png";
    return switch (mime.toLowerCase()) { case "image/jpeg", "image/jpg" -> "jpg"; case "image/gif" -> "gif"; case "image/bmp" -> "bmp"; case "image/emf" -> "emf"; case "image/wmf" -> "wmf"; default -> "png"; };
  }
  private record ColumnLayout(boolean twoColumns, double split) {}
}
