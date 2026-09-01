package com.officebox.common.conversion;

import com.officebox.common.conversion.model.ImageBlock;
import com.officebox.common.conversion.model.PageBlock;
import com.officebox.common.conversion.model.PageModel;
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
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.Document;
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
      for (int i = 0; i < pages.size(); i++) {
        renderPage(document, pages.get(i));
        if (i + 1 < pages.size()) {
          XWPFParagraph pageBreak = document.createParagraph();
          pageBreak.setPageBreak(true);
        }
      }
      try (OutputStream stream = Files.newOutputStream(output)) { document.write(stream); }
    }
    return output;
  }

  private void renderPage(XWPFDocument document, PageModel page) {
    CTSectPr section = document.getDocument().getBody().isSetSectPr()
        ? document.getDocument().getBody().getSectPr() : document.getDocument().getBody().addNewSectPr();
    CTPageSz pageSize = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
    pageSize.setW(BigInteger.valueOf(twips(page.width())));
    pageSize.setH(BigInteger.valueOf(twips(page.height())));
    CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
    margins.setTop(BigInteger.ZERO); margins.setBottom(BigInteger.ZERO);
    margins.setLeft(BigInteger.ZERO); margins.setRight(BigInteger.ZERO);

    List<TextBlock> textBlocks = page.blocks().stream()
        .filter(TextBlock.class::isInstance).map(TextBlock.class::cast)
        .sorted(Comparator.comparingDouble((TextBlock b) -> b.bounds().y()).thenComparingDouble(b -> b.bounds().x()))
        .toList();
    if (textBlocks.isEmpty() && page.blocks().stream().noneMatch(ImageBlock.class::isInstance)) return;

    ColumnLayout columns = detectColumns(textBlocks, page.width());
    if (columns.twoColumns) renderTwoColumns(document, page.blocks(), columns.split, page.width());
    else renderSingleColumn(document, page.blocks(), page.width());
  }

  private void renderSingleColumn(XWPFDocument document, List<PageBlock> blocks, double pageWidth) {
    double previousBottom = 0;
    for (PageBlock block : blocks.stream().sorted(Comparator.comparingDouble((PageBlock b) -> b.bounds().y())
        .thenComparingDouble(b -> b.bounds().x())).toList()) {
      XWPFParagraph paragraph = document.createParagraph();
      double yGap = Math.max(0, block.bounds().y() - previousBottom);
      paragraph.setSpacingBefore((int) Math.round(yGap));
      paragraph.setSpacingAfter(0);
      paragraph.setIndentationLeft((int) Math.round(Math.max(0, block.bounds().x())));
      if (block instanceof TextBlock text) {
        writeSpans(paragraph, text.spans());
      } else if (block instanceof ImageBlock image) {
        writeImage(paragraph, image);
      }
      previousBottom = Math.max(previousBottom, block.bounds().bottom());
    }
  }

  private void renderTwoColumns(XWPFDocument document, List<PageBlock> blocks, double split, double pageWidth) {
    List<PageBlock> left = new ArrayList<>(), right = new ArrayList<>();
    for (PageBlock block : blocks) {
      double center = block.bounds().x() + block.bounds().width() / 2.0;
      if (center < split) left.add(block); else right.add(block);
    }
    XWPFTable table = document.createTable(1, 2);
    table.setWidth("100%");
    table.getCTTbl().getTblPr().unsetTblBorders();
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
    for (PageBlock block : blocks.stream().sorted(Comparator.comparingDouble((PageBlock b) -> b.bounds().y())
        .thenComparingDouble(b -> b.bounds().x())).toList()) {
      XWPFParagraph paragraph = cell.addParagraph();
      double yGap = Math.max(0, block.bounds().y() - previousBottom);
      paragraph.setSpacingBefore((int) Math.round(yGap));
      paragraph.setSpacingAfter(0);
      paragraph.setIndentationLeft((int) Math.round(Math.max(0, block.bounds().x() - originX)));
      if (block instanceof TextBlock text) writeSpans(paragraph, text.spans());
      else if (block instanceof ImageBlock image) writeImage(paragraph, image);
      previousBottom = Math.max(previousBottom, block.bounds().bottom());
    }
  }

  private static void writeImage(XWPFParagraph paragraph, ImageBlock image) {
    try {
      XWPFRun run = paragraph.createRun();
      int width = Math.max(1, Units.toEMU(image.bounds().width()));
      int height = Math.max(1, Units.toEMU(image.bounds().height()));
      run.addPicture(new ByteArrayInputStream(image.data()), Document.PICTURE_TYPE_PNG,
          "pdf-image.png", width, height);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to embed PDF image", e);
    }
  }

  private static void writeSpans(XWPFParagraph paragraph, List<TextSpan> spans) {
    for (TextSpan span : spans) {
      XWPFRun run = paragraph.createRun();
      run.setText(span.text());
      run.setFontSize((float) Math.max(1, span.fontSize()));
      String font = normalizeFont(span.fontName());
      if (!font.isBlank()) run.setFontFamily(font);
      run.setBold(span.bold()); run.setItalic(span.italic());
      if (span.red() != 0 || span.green() != 0 || span.blue() != 0) {
        run.setColor(String.format("%02X%02X%02X", span.red(), span.green(), span.blue()));
      }
    }
  }

  private static ColumnLayout detectColumns(List<TextBlock> blocks, double pageWidth) {
    if (blocks.size() < 6) return new ColumnLayout(false, pageWidth);
    List<Double> xs = blocks.stream().map(b -> b.bounds().x()).sorted().toList();
    double bestGap = 0, split = pageWidth;
    for (int i = 1; i < xs.size(); i++) {
      double a = xs.get(i - 1), b = xs.get(i), gap = b - a;
      if (a > pageWidth * .12 && b < pageWidth * .88 && gap > bestGap) { bestGap = gap; split = (a + b) / 2; }
    }
    boolean two = bestGap >= Math.max(28, pageWidth * .08);
    if (two) {
      long left = blocks.stream().filter(b -> b.bounds().x() < split).count();
      long right = blocks.size() - left;
      two = left >= 2 && right >= 2;
    }
    return new ColumnLayout(two, split);
  }

  private static void clearCell(XWPFTableCell cell) { while (!cell.getParagraphs().isEmpty()) cell.removeParagraph(0); }
  private static void setCellWidth(XWPFTableCell cell, double points) { cell.setWidth(String.format("%.0fpt", Math.max(1, points))); }
  private static int twips(double points) { return (int) Math.round(points * 20.0); }
  private static String normalizeFont(String font) {
    if (font == null) return "";
    int plus = font.indexOf('+');
    return plus >= 0 && plus + 1 < font.length() ? font.substring(plus + 1) : font;
  }
  private record ColumnLayout(boolean twoColumns, double split) {}
}
