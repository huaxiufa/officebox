package com.officebox.common.conversion;

import com.officebox.common.conversion.model.BoundingBox;
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
import java.util.Locale;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.springframework.stereotype.Component;

/** Renders the coordinate-aware PDF model as editable DOCX content. */
@Component
public class DocxRenderer {
  private static final double MIN_FONT_PT = 5.0;
  private static final double MAX_FONT_PT = 72.0;

  private final PdfLayoutAnalyzer layoutAnalyzer = new PdfLayoutAnalyzer();

  public Path render(List<PageModel> pages, Path output) throws IOException {
    if (pages == null || pages.isEmpty()) throw new IOException("No PDF pages to render");
    Path normalizedOutput = output.toAbsolutePath().normalize();
    Path parent = normalizedOutput.getParent();
    if (parent != null) Files.createDirectories(parent);

    try (XWPFDocument document = new XWPFDocument()) {
      for (int i = 0; i < pages.size(); i++) {
        renderPage(document, pages.get(i));
        if (i + 1 < pages.size()) document.createParagraph().setPageBreak(true);
      }
      try (OutputStream stream = Files.newOutputStream(normalizedOutput)) {
        document.write(stream);
      }
    }
    return normalizedOutput;
  }

  private void renderPage(XWPFDocument document, PageModel page) {
    configurePage(document, page);
    List<PageBlock> blocks = layoutAnalyzer.analyze(page.blocks());
    List<TextBlock> textBlocks = blocks.stream()
        .filter(TextBlock.class::isInstance).map(TextBlock.class::cast)
        .sorted(Comparator.comparingDouble((TextBlock b) -> b.bounds().y())
            .thenComparingDouble(b -> b.bounds().x())).toList();

    if (textBlocks.isEmpty() && blocks.stream().noneMatch(ImageBlock.class::isInstance)
        && blocks.stream().noneMatch(TableBlock.class::isInstance)) return;

    ColumnLayout columns = detectColumns(textBlocks, page.width());
    if (columns.twoColumns()) {
      renderTwoColumns(document, blocks, columns.split(), page.width());
    } else {
      renderSingleColumn(document, blocks);
    }
  }

  private void configurePage(XWPFDocument document, PageModel page) {
    CTSectPr section = document.getDocument().getBody().isSetSectPr()
        ? document.getDocument().getBody().getSectPr()
        : document.getDocument().getBody().addNewSectPr();
    CTPageSz pageSize = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
    pageSize.setW(BigInteger.valueOf(twips(page.width())));
    pageSize.setH(BigInteger.valueOf(twips(page.height())));
    CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
    margins.setTop(BigInteger.ZERO);
    margins.setBottom(BigInteger.ZERO);
    margins.setLeft(BigInteger.ZERO);
    margins.setRight(BigInteger.ZERO);
  }

  private void renderSingleColumn(XWPFDocument document, List<PageBlock> blocks) {
    double previousBottom = 0;
    for (PageBlock block : sorted(blocks)) {
      if (block instanceof TableBlock table) {
        addVerticalSpacer(document, table.bounds(), previousBottom);
        renderTable(document, table);
        previousBottom = Math.max(previousBottom, table.bounds().bottom());
      } else {
        XWPFParagraph paragraph = document.createParagraph();
        positionParagraph(paragraph, block.bounds(), previousBottom, 0);
        if (block instanceof TextBlock text) writeSpans(paragraph, text.spans());
        else if (block instanceof ImageBlock image) writeImage(paragraph, image);
        previousBottom = Math.max(previousBottom, block.bounds().bottom());
      }
    }
  }

  private void renderTwoColumns(XWPFDocument document, List<PageBlock> blocks, double split, double pageWidth) {
    List<PageBlock> left = new ArrayList<>();
    List<PageBlock> right = new ArrayList<>();
    for (PageBlock block : blocks) {
      double center = block.bounds().x() + block.bounds().width() / 2.0;
      if (center < split) left.add(block); else right.add(block);
    }

    XWPFTable table = document.createTable(1, 2);
    table.setWidth("100%");
    CTTblLayoutType layout = table.getCTTbl().getTblPr().isSetTblLayout()
        ? table.getCTTbl().getTblPr().getTblLayout()
        : table.getCTTbl().getTblPr().addNewTblLayout();
    layout.setType(STTblLayoutType.FIXED);
    table.getCTTbl().getTblPr().unsetTblBorders();
    XWPFTableRow row = table.getRow(0);
    setCellWidth(row.getCell(0), split);
    setCellWidth(row.getCell(1), pageWidth - split);
    clearCell(row.getCell(0));
    clearCell(row.getCell(1));
    renderColumnCell(row.getCell(0), left, 0);
    renderColumnCell(row.getCell(1), right, split);
  }

  private void renderColumnCell(XWPFTableCell cell, List<PageBlock> blocks, double originX) {
    double previousBottom = 0;
    for (PageBlock block : sorted(blocks)) {
      if (block instanceof TableBlock table) {
        XWPFParagraph anchor = cell.addParagraph();
        positionParagraph(anchor, table.bounds(), previousBottom, originX);
        renderTableIntoCell(cell, table);
      } else {
        XWPFParagraph paragraph = cell.addParagraph();
        positionParagraph(paragraph, block.bounds(), previousBottom, originX);
        if (block instanceof TextBlock text) writeSpans(paragraph, text.spans());
        else if (block instanceof ImageBlock image) writeImage(paragraph, image);
      }
      previousBottom = Math.max(previousBottom, block.bounds().bottom());
    }
  }

  private void positionParagraph(XWPFParagraph paragraph, BoundingBox bounds, double previousBottom, double originX) {
    double gap = Math.max(0, bounds.y() - previousBottom);
    paragraph.setSpacingBefore((int) Math.min(240, Math.round(gap)));
    paragraph.setSpacingAfter(0);
    paragraph.setIndentationLeft(twips(Math.max(0, bounds.x() - originX)));
  }

  private void addVerticalSpacer(XWPFDocument document, BoundingBox bounds, double previousBottom) {
    XWPFParagraph spacer = document.createParagraph();
    spacer.setSpacingBefore((int) Math.min(240, Math.round(Math.max(0, bounds.y() - previousBottom))));
    spacer.setSpacingAfter(0);
  }

  private void writeSpans(XWPFParagraph paragraph, List<TextSpan> spans) {
    for (TextSpan span : spans) {
      XWPFRun run = paragraph.createRun();
      if (span.text() != null) run.setText(span.text());
      run.setFontSize((float) Math.max(MIN_FONT_PT, Math.min(MAX_FONT_PT, span.fontSize())));
      String font = normalizeFont(span.fontName());
      if (!font.isBlank()) run.setFontFamily(font);
      run.setBold(span.bold());
      run.setItalic(span.italic());
      if (span.red() != 0 || span.green() != 0 || span.blue() != 0) {
        run.setColor(String.format(Locale.ROOT, "%02X%02X%02X", span.red(), span.green(), span.blue()));
      }
    }
  }

  private void writeImage(XWPFParagraph paragraph, ImageBlock image) {
    try {
      XWPFRun run = paragraph.createRun();
      run.addPicture(new ByteArrayInputStream(image.data()), pictureType(image.mimeType()),
          "pdf-image." + extension(image.mimeType()),
          Math.max(1, Units.toEMU(image.bounds().width())),
          Math.max(1, Units.toEMU(image.bounds().height())));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to embed PDF image", e);
    }
  }

  private void renderTable(XWPFDocument document, TableBlock table) {
    XWPFTable wordTable = document.createTable(table.rows().size(), maxColumns(table));
    configureTable(wordTable, table);
    fillTable(wordTable, table);
  }

  private void renderTableIntoCell(XWPFTableCell parentCell, TableBlock table) {
    for (List<TextBlock> row : table.rows()) {
      XWPFParagraph paragraph = parentCell.addParagraph();
      paragraph.setSpacingBefore(0);
      paragraph.setSpacingAfter(0);
      boolean first = true;
      for (TextBlock cell : row) {
        if (!first) paragraph.createRun().setText("\t");
        writeSpans(paragraph, cell.spans());
        first = false;
      }
    }
  }

  private void configureTable(XWPFTable table, TableBlock source) {
    table.setWidth("100%");
    CTTblLayoutType layout = table.getCTTbl().getTblPr().isSetTblLayout()
        ? table.getCTTbl().getTblPr().getTblLayout()
        : table.getCTTbl().getTblPr().addNewTblLayout();
    layout.setType(STTblLayoutType.FIXED);
    table.getCTTbl().getTblPr().unsetTblBorders();
    double totalWidth = Math.max(1, source.bounds().width());
    int columns = maxColumns(source);
    for (int c = 0; c < columns; c++) {
      double width = totalWidth / columns;
      for (int r = 0; r < table.getNumberOfRows(); r++) setCellWidth(table.getRow(r).getCell(c), width);
    }
  }

  private void fillTable(XWPFTable table, TableBlock source) {
    int columns = maxColumns(source);
    for (int r = 0; r < source.rows().size(); r++) {
      XWPFTableRow row = table.getRow(r);
      for (int c = 0; c < columns; c++) {
        XWPFTableCell cell = row.getCell(c);
        clearCell(cell);
        if (c < source.rows().get(r).size()) {
          XWPFParagraph paragraph = cell.getParagraphs().get(0);
          paragraph.setSpacingBefore(0);
          paragraph.setSpacingAfter(0);
          writeSpans(paragraph, source.rows().get(r).get(c).spans());
        }
      }
    }
  }

  private int maxColumns(TableBlock table) {
    return Math.max(1, table.rows().stream().mapToInt(List::size).max().orElse(1));
  }

  private static void clearCell(XWPFTableCell cell) {
    while (cell.getParagraphs().size() > 1) cell.removeParagraph(0);
    XWPFParagraph paragraph = cell.getParagraphs().get(0);
    while (!paragraph.getRuns().isEmpty()) paragraph.removeRun(0);
  }

  private static void setCellWidth(XWPFTableCell cell, double points) {
    cell.setWidth(String.format(Locale.ROOT, "%.0fpt", Math.max(1, points)));
  }

  private static ColumnLayout detectColumns(List<TextBlock> blocks, double pageWidth) {
    if (blocks.size() < 6) return new ColumnLayout(false, pageWidth);
    List<Double> xs = blocks.stream().map(b -> b.bounds().x()).sorted().toList();
    double bestGap = 0;
    double candidateSplit = pageWidth;
    for (int i = 1; i < xs.size(); i++) {
      double a = xs.get(i - 1);
      double b = xs.get(i);
      double gap = b - a;
      if (a > pageWidth * .12 && b < pageWidth * .88 && gap > bestGap) {
        bestGap = gap;
        candidateSplit = (a + b) / 2;
      }
    }
    final double split = candidateSplit;
    boolean two = bestGap >= Math.max(28, pageWidth * .08);
    if (two) {
      long left = blocks.stream().filter(block -> block.bounds().x() < split).count();
      two = left >= 2 && blocks.size() - left >= 2;
    }
    return new ColumnLayout(two, split);
  }

  private static List<PageBlock> sorted(List<PageBlock> blocks) {
    return blocks.stream().sorted(Comparator.comparingDouble((PageBlock b) -> b.bounds().y())
        .thenComparingDouble(b -> b.bounds().x())).toList();
  }

  private static int twips(double points) {
    return (int) Math.round(points * 20);
  }

  private static String normalizeFont(String font) {
    if (font == null) return "";
    String normalized = font.trim();
    int plus = normalized.indexOf('+');
    if (plus >= 0 && plus + 1 < normalized.length()) normalized = normalized.substring(plus + 1);
    if (normalized.equalsIgnoreCase("TimesNewRomanPSMT")) return "Times New Roman";
    if (normalized.equalsIgnoreCase("ArialMT")) return "Arial";
    if (normalized.equalsIgnoreCase("SimSun")) return "宋体";
    if (normalized.equalsIgnoreCase("SimHei")) return "黑体";
    return normalized;
  }

  private static int pictureType(String mime) {
    if (mime == null) return Document.PICTURE_TYPE_PNG;
    return switch (mime.toLowerCase(Locale.ROOT)) {
      case "image/jpeg", "image/jpg" -> Document.PICTURE_TYPE_JPEG;
      case "image/gif" -> Document.PICTURE_TYPE_GIF;
      case "image/bmp" -> Document.PICTURE_TYPE_BMP;
      case "image/emf" -> Document.PICTURE_TYPE_EMF;
      case "image/wmf" -> Document.PICTURE_TYPE_WMF;
      default -> Document.PICTURE_TYPE_PNG;
    };
  }

  private static String extension(String mime) {
    if (mime == null) return "png";
    return switch (mime.toLowerCase(Locale.ROOT)) {
      case "image/jpeg", "image/jpg" -> "jpg";
      case "image/gif" -> "gif";
      case "image/bmp" -> "bmp";
      case "image/emf" -> "emf";
      case "image/wmf" -> "wmf";
      default -> "png";
    };
  }

  private record ColumnLayout(boolean twoColumns, double split) {}
}
