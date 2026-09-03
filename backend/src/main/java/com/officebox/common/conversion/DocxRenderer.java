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
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTableCell.XWPFVertAlign;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Component;

/** Renders the coordinate-aware PDF model as editable, styled DOCX content. */
@Component
public class DocxRenderer {
  private static final String BODY_STYLE = "OfficeBoxBody";
  private static final String HEADING_STYLE = "OfficeBoxHeading";
  private static final String ACCENT = "1F4E79";
  private static final String TEXT = "222222";
  private static final String MUTED = "666666";
  private static final String DEFAULT_FONT = "Aptos";
  private static final String CJK_FONT = "等线";

  public Path render(List<PageModel> pages, Path output) throws IOException {
    if (pages == null || pages.isEmpty()) throw new IOException("No PDF pages to render");
    Path parent = output.toAbsolutePath().normalize().getParent();
    if (parent != null) Files.createDirectories(parent);

    try (XWPFDocument document = new XWPFDocument()) {
      configureStyles(document);
      XWPFHeaderFooterPolicy headerFooter = document.createHeaderFooterPolicy();
      addFooter(headerFooter);
      for (int i = 0; i < pages.size(); i++) {
        renderPage(document, pages.get(i));
        if (i + 1 < pages.size()) {
          XWPFParagraph pageBreak = document.createParagraph();
          pageBreak.setPageBreak(true);
          pageBreak.setSpacingBefore(0);
          pageBreak.setSpacingAfter(0);
        }
      }
      try (OutputStream stream = Files.newOutputStream(output)) {
        document.write(stream);
      }
    }
    return output;
  }

  private static void configureStyles(XWPFDocument document) {
    XWPFStyles styles = document.createStyles();
    addStyle(styles, BODY_STYLE, "Normal", 10.5, TEXT, false, 1.15, 0, 5);
    addStyle(styles, HEADING_STYLE, "Normal", 15, ACCENT, true, 1.0, 14, 7);

    CTStyle normal = styles.getStyle("Normal");
    if (normal == null) normal = styles.getCTStyles().addNewStyle();
    if (!normal.isSetRPr()) normal.addNewRPr();
    if (!normal.getRPr().isSetRFonts()) normal.getRPr().addNewRFonts();
    normal.getRPr().getRFonts().setAscii(DEFAULT_FONT);
    normal.getRPr().getRFonts().setHAnsi(DEFAULT_FONT);
    normal.getRPr().getRFonts().setEastAsia(CJK_FONT);
    normal.getRPr().addNewSz().setVal(BigInteger.valueOf(21));
  }

  private static void addStyle(XWPFStyles styles, String id, String basedOn, double size,
      String color, boolean bold, double lineSpacing, int before, int after) {
    CTStyle style = styles.getCTStyles().addNewStyle();
    style.setStyleId(id);
    style.addNewName().setVal(id);
    style.addNewBasedOn().setVal(basedOn);
    style.addNewPPr().addNewSpacing().setBefore(BigInteger.valueOf(before * 20L));
    style.getPPr().getSpacing().setAfter(BigInteger.valueOf(after * 20L));
    style.getPPr().getSpacing().setLine(BigInteger.valueOf(Math.round(lineSpacing * 240)));
    style.addNewRPr().addNewRFonts().setAscii(DEFAULT_FONT);
    style.getRPr().getRFonts().setHAnsi(DEFAULT_FONT);
    style.getRPr().getRFonts().setEastAsia(CJK_FONT);
    style.getRPr().addNewSz().setVal(BigInteger.valueOf(Math.round(size * 2)));
    style.getRPr().addNewColor().setVal(color);
    if (bold) style.getRPr().addNewB();
  }

  private static void addFooter(XWPFHeaderFooterPolicy policy) {
    XWPFHeader footerContainer = null;
    var footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT);
    XWPFParagraph paragraph = footer.createParagraph();
    paragraph.setAlignment(ParagraphAlignment.CENTER);
    paragraph.setSpacingBefore(0);
    paragraph.setSpacingAfter(0);
    XWPFRun label = paragraph.createRun();
    label.setFontFamily(DEFAULT_FONT);
    label.setFontSize(8);
    label.setColor(MUTED);
    label.setText("OfficeBox  •  ");
    XWPFRun page = paragraph.createRun();
    page.getCTR().addNewFldChar().setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.BEGIN);
    page.getCTR().addNewInstrText().setStringValue(" PAGE ");
    page.getCTR().addNewFldChar().setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.END);
  }

  private void renderPage(XWPFDocument document, PageModel page) {
    configurePage(document, page);
    List<TextBlock> textBlocks = page.blocks().stream()
        .filter(TextBlock.class::isInstance).map(TextBlock.class::cast)
        .sorted(Comparator.comparingDouble((TextBlock b) -> b.bounds().y())
            .thenComparingDouble(b -> b.bounds().x())).toList();
    List<PageBlock> blocks = page.blocks().stream()
        .filter(block -> !(block instanceof ImageBlock image
            && image.bounds().x() <= .5 && image.bounds().y() <= .5
            && image.bounds().width() >= page.width() * .95
            && image.bounds().height() >= page.height() * .95)).toList();
    if (blocks.isEmpty()) return;
    ColumnLayout columns = detectColumns(textBlocks, page.width());
    if (columns.twoColumns) renderTwoColumns(document, blocks, columns.split, page.width());
    else renderSingleColumn(document, blocks);
  }

  private static void configurePage(XWPFDocument document, PageModel page) {
    CTSectPr section = document.getDocument().getBody().isSetSectPr()
        ? document.getDocument().getBody().getSectPr() : document.getDocument().getBody().addNewSectPr();
    CTPageSz pageSize = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
    pageSize.setW(BigInteger.valueOf(twips(page.width())));
    pageSize.setH(BigInteger.valueOf(twips(page.height())));
    CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
    margins.setTop(BigInteger.valueOf(twips(36)));
    margins.setBottom(BigInteger.valueOf(twips(36)));
    margins.setLeft(BigInteger.valueOf(twips(42)));
    margins.setRight(BigInteger.valueOf(twips(42)));
    margins.setHeader(BigInteger.valueOf(twips(18)));
    margins.setFooter(BigInteger.valueOf(twips(18)));
  }

  private static void renderSingleColumn(XWPFDocument document, List<PageBlock> blocks) {
    double previousBottom = 0;
    for (PageBlock block : sorted(blocks)) {
      XWPFParagraph paragraph = document.createParagraph();
      boolean heading = block instanceof TextBlock text && text.heading();
      styleParagraph(paragraph, block, heading);
      double gap = Math.max(0, block.bounds().y() - previousBottom);
      paragraph.setSpacingBefore(Math.min(twips(gap), twips(24)));
      paragraph.setIndentationLeft(twips(Math.max(0, block.bounds().x())));
      if (block instanceof TextBlock text) writeTextBlock(paragraph, text);
      else if (block instanceof ImageBlock image) writeImage(paragraph, image);
      previousBottom = Math.max(previousBottom, block.bounds().bottom());
    }
  }

  private static void renderTwoColumns(XWPFDocument document, List<PageBlock> blocks, double split, double pageWidth) {
    XWPFTable table = document.createTable(1, 2);
    table.setWidth("100%");
    removeTableBorders(table);
    setTableLayoutFixed(table);
    XWPFTableRow row = table.getRow(0);
    XWPFTableCell leftCell = row.getCell(0), rightCell = row.getCell(1);
    setCellWidth(leftCell, split);
    setCellWidth(rightCell, pageWidth - split);
    prepareCell(leftCell);
    prepareCell(rightCell);
    List<PageBlock> left = new ArrayList<>(), right = new ArrayList<>();
    for (PageBlock block : blocks) {
      double center = block.bounds().x() + block.bounds().width() / 2;
      if (center < split) left.add(block); else right.add(block);
    }
    renderCell(leftCell, left, 0);
    renderCell(rightCell, right, split);
  }

  private static void renderCell(XWPFTableCell cell, List<PageBlock> blocks, double originX) {
    double previousBottom = 0;
    for (PageBlock block : sorted(blocks)) {
      XWPFParagraph paragraph = cell.addParagraph();
      boolean heading = block instanceof TextBlock text && text.heading();
      styleParagraph(paragraph, block, heading);
      double gap = Math.max(0, block.bounds().y() - previousBottom);
      paragraph.setSpacingBefore(Math.min(twips(gap), twips(18)));
      paragraph.setIndentationLeft(twips(Math.max(0, block.bounds().x() - originX)));
      if (block instanceof TextBlock text) writeTextBlock(paragraph, text);
      else if (block instanceof ImageBlock image) writeImage(paragraph, image);
      previousBottom = Math.max(previousBottom, block.bounds().bottom());
    }
  }

  private static void styleParagraph(XWPFParagraph paragraph, PageBlock block, boolean heading) {
    paragraph.setStyle(heading ? HEADING_STYLE : BODY_STYLE);
    paragraph.setSpacingAfter(heading ? twips(7) : twips(5));
    paragraph.setLineSpacing(heading ? 1.0 : 1.15);
    paragraph.setAlignment(ParagraphAlignment.LEFT);
    paragraph.setKeepNext(heading);
    paragraph.setKeepLines(heading);
    paragraph.setWidowControl(true);
    if (block instanceof TextBlock text && isBullet(text.text())) {
      paragraph.setIndentationLeft(twips(16));
      paragraph.setIndentationHanging(twips(10));
    }
  }

  private static void writeTextBlock(XWPFParagraph paragraph, TextBlock block) {
    boolean bullet = isBullet(block.text());
    boolean heading = block.heading();
    String firstSpanText = block.spans().isEmpty() ? "" : block.spans().get(0).text();
    for (TextSpan span : block.spans()) {
      XWPFRun run = paragraph.createRun();
      String value = span.text();
      if (bullet && value.equals(firstSpanText)) value = value.replaceFirst("^[•·]\\s*", "");
      run.setText(value);
      double size = heading ? Math.max(13, Math.min(16, span.fontSize() + 1.5))
          : Math.max(8.5, Math.min(13, span.fontSize()));
      run.setFontSize((float) size);
      run.setFontFamily(normalizeFont(span.fontName()).isBlank() ? DEFAULT_FONT : normalizeFont(span.fontName()));
      run.setBold(span.bold() || heading);
      run.setItalic(span.italic());
      if (span.red() != 0 || span.green() != 0 || span.blue() != 0) {
        run.setColor(String.format("%02X%02X%02X", span.red(), span.green(), span.blue()));
      } else {
        run.setColor(heading ? ACCENT : TEXT);
      }
    }
    if (bullet) {
      paragraph.setIndentationLeft(twips(16));
      paragraph.setIndentationHanging(twips(10));
      XWPFRun bulletRun = paragraph.getRuns().isEmpty() ? paragraph.createRun() : paragraph.getRuns().get(0);
      if (!bulletRun.getText(0).contains("•")) {
        bulletRun.setText("• " + bulletRun.getText(0), 0);
      }
    }
  }

  private static boolean isBullet(String text) {
    if (text == null) return false;
    String value = text.trim();
    return value.startsWith("•") || value.startsWith("·") || value.startsWith("");
  }

  private static void writeImage(XWPFParagraph paragraph, ImageBlock image) {
    try {
      paragraph.setAlignment(ParagraphAlignment.CENTER);
      XWPFRun run = paragraph.createRun();
      run.addPicture(new ByteArrayInputStream(image.data()), pictureType(image.mimeType()),
          "pdf-image." + extension(image.mimeType()), Math.max(1, Units.toEMU(image.bounds().width())),
          Math.max(1, Units.toEMU(image.bounds().height())));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to embed PDF image", e);
    }
  }

  private static void prepareCell(XWPFTableCell cell) {
    cell.setVerticalAlignment(XWPFVertAlign.TOP);
    while (!cell.getParagraphs().isEmpty()) cell.removeParagraph(0);
  }

  private static void removeTableBorders(XWPFTable table) {
    CTTblBorders borders = table.getCTTbl().getTblPr().isSetTblBorders()
        ? table.getCTTbl().getTblPr().getTblBorders() : table.getCTTbl().getTblPr().addNewTblBorders();
    borders.addNewTop().setVal(STBorder.NONE); borders.addNewBottom().setVal(STBorder.NONE);
    borders.addNewLeft().setVal(STBorder.NONE); borders.addNewRight().setVal(STBorder.NONE);
    borders.addNewInsideH().setVal(STBorder.NONE); borders.addNewInsideV().setVal(STBorder.NONE);
  }

  private static void setTableLayoutFixed(XWPFTable table) {
    CTTblLayoutType layout = table.getCTTbl().getTblPr().isSetTblLayout()
        ? table.getCTTbl().getTblPr().getTblLayout() : table.getCTTbl().getTblPr().addNewTblLayout();
    layout.setType(STTblLayoutType.FIXED);
  }

  private static void setCellWidth(XWPFTableCell cell, double points) {
    CTTcPr pr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
    CTTblWidth width = pr.isSetTcW() ? pr.getTcW() : pr.addNewTcW();
    width.setType(STTblWidth.DXA);
    width.setW(BigInteger.valueOf(twips(points)));
  }

  private static int pictureType(String mime) {
    if (mime == null) return Document.PICTURE_TYPE_PNG;
    return switch (mime.toLowerCase()) {
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
    return switch (mime.toLowerCase()) {
      case "image/jpeg", "image/jpg" -> "jpg";
      case "image/gif" -> "gif";
      case "image/bmp" -> "bmp";
      case "image/emf" -> "emf";
      case "image/wmf" -> "wmf";
      default -> "png";
    };
  }

  private static ColumnLayout detectColumns(List<TextBlock> blocks, double pageWidth) {
    if (blocks.size() < 8) return new ColumnLayout(false, pageWidth);
    List<Double> xs = blocks.stream().map(b -> b.bounds().x()).sorted().toList();
    double bestGap = 0, split = pageWidth;
    for (int i = 1; i < xs.size(); i++) {
      double a = xs.get(i - 1), b = xs.get(i), gap = b - a;
      if (a > pageWidth * .12 && b < pageWidth * .88 && gap > bestGap) {
        bestGap = gap; split = (a + b) / 2;
      }
    }
    boolean two = bestGap >= Math.max(28, pageWidth * .08);
    if (two) {
      long left = blocks.stream().filter(block -> block.bounds().x() < split).count();
      two = left >= 3 && blocks.size() - left >= 3;
    }
    return new ColumnLayout(two, split);
  }

  private static List<PageBlock> sorted(List<PageBlock> blocks) {
    return blocks.stream().sorted(Comparator.comparingDouble((PageBlock b) -> b.bounds().y())
        .thenComparingDouble(b -> b.bounds().x())).toList();
  }

  private static int twips(double points) { return (int) Math.round(points * 20); }

  private static String normalizeFont(String font) {
    if (font == null) return "";
    int plus = font.indexOf('+');
    return plus >= 0 && plus + 1 < font.length() ? font.substring(plus + 1) : font;
  }

  private record ColumnLayout(boolean twoColumns, double split) {}
}
