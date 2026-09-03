package com.officebox.common.conversion;

import com.officebox.common.conversion.model.BoundingBox;
import com.officebox.common.conversion.model.ImageBlock;
import com.officebox.common.conversion.model.PageBlock;
import com.officebox.common.conversion.model.PageModel;
import com.officebox.common.conversion.model.TableBlock;
import com.officebox.common.conversion.model.TextBlock;
import com.officebox.common.conversion.model.TextSpan;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/** Renders PDF page geometry into an editable native DOCX structure. */
public final class DocxRenderer {
    private static final double POINTS_PER_INCH = 72.0;
    private static final double PAGE_MARGIN_INCH = 0.25;
    private static final double MIN_FONT_PT = 5.0;
    private static final double MAX_FONT_PT = 72.0;

    public void render(List<PageModel> pages, XWPFDocument document) throws IOException {
        if (pages == null || pages.isEmpty()) return;

        PageModel first = pages.getFirst();
        configurePage(document, first);

        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            if (pageIndex > 0) addPageBreak(document);
            PageModel page = pages.get(pageIndex);
            if (PdfLayoutAnalyzer.hasTwoColumns(page)) {
                renderTwoColumns(page, document);
            } else {
                renderSingleColumn(page, document);
            }
        }
    }

    private void configurePage(XWPFDocument document, PageModel page) {
        double widthInches = Math.max(1.0, page.width() / POINTS_PER_INCH);
        double heightInches = Math.max(1.0, page.height() / POINTS_PER_INCH);
        var sectPr = document.getDocument().getBody().isSetSectPr()
            ? document.getDocument().getBody().getSectPr()
            : document.getDocument().getBody().addNewSectPr();
        var pgSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pgSz.setW((long) Math.round(widthInches * POINTS_PER_INCH * 20));
        pgSz.setH((long) Math.round(heightInches * POINTS_PER_INCH * 20));
        var pgMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pgMar.setLeft((long) Math.round(PAGE_MARGIN_INCH * POINTS_PER_INCH * 20));
        pgMar.setRight((long) Math.round(PAGE_MARGIN_INCH * POINTS_PER_INCH * 20));
        pgMar.setTop((long) Math.round(PAGE_MARGIN_INCH * POINTS_PER_INCH * 20));
        pgMar.setBottom((long) Math.round(PAGE_MARGIN_INCH * POINTS_PER_INCH * 20));
    }

    private void renderSingleColumn(PageModel page, XWPFDocument document) throws IOException {
        double previousBottom = 0;
        double originX = 0;
        for (PageBlock block : page.blocks()) {
            if (block instanceof TableBlock table) {
                XWPFParagraph anchor = document.createParagraph();
                positionParagraph(anchor, table.bounds(), previousBottom, originX);
                writeTableAfter(anchor, table);
                previousBottom = table.bounds().bottom();
            } else if (block instanceof ImageBlock image) {
                if (isFullPageImage(image, page)) continue;
                XWPFParagraph paragraph = document.createParagraph();
                positionParagraph(paragraph, image.bounds(), previousBottom, originX);
                writeImage(paragraph, image);
                previousBottom = image.bounds().bottom();
            } else if (block instanceof TextBlock text) {
                XWPFParagraph paragraph = document.createParagraph();
                positionParagraph(paragraph, text.bounds(), previousBottom, originX);
                writeSpans(paragraph, text.spans());
                previousBottom = text.bounds().bottom();
            }
        }
    }

    private void renderTwoColumns(PageModel page, XWPFDocument document) throws IOException {
        List<PageBlock> left = PdfLayoutAnalyzer.leftColumn(page);
        List<PageBlock> right = PdfLayoutAnalyzer.rightColumn(page);
        XWPFTable table = document.createTable(1, 2);
        table.setWidth("100%");
        table.getCTTbl().getTblPr().unsetTblBorders();
        table.getRow(0).getCell(0).setWidth("50%");
        table.getRow(0).getCell(1).setWidth("50%");
        renderColumnCell(table.getRow(0).getCell(0), left, page);
        renderColumnCell(table.getRow(0).getCell(1), right, page);
    }

    private void renderColumnCell(XWPFTableCell cell, List<PageBlock> blocks, PageModel page) throws IOException {
        clearCell(cell);
        double previousBottom = 0;
        double originX = blocks.isEmpty() ? 0 : blocks.getFirst().bounds().left();
        for (PageBlock block : blocks) {
            if (block instanceof TableBlock nested) {
                XWPFParagraph anchor = cell.addParagraph();
                positionParagraph(anchor, nested.bounds(), previousBottom, originX);
                for (List<TextBlock> row : nested.rows()) {
                    for (TextBlock text : row) {
                        XWPFParagraph paragraph = cell.addParagraph();
                        writeSpans(paragraph, text.spans());
                    }
                }
            } else if (block instanceof ImageBlock image) {
                if (isFullPageImage(image, page)) continue;
                XWPFParagraph paragraph = cell.addParagraph();
                positionParagraph(paragraph, image.bounds(), previousBottom, originX);
                writeImage(paragraph, image);
                previousBottom = image.bounds().bottom();
            } else if (block instanceof TextBlock text) {
                XWPFParagraph paragraph = cell.addParagraph();
                positionParagraph(paragraph, text.bounds(), previousBottom, originX);
                writeSpans(paragraph, text.spans());
                previousBottom = text.bounds().bottom();
            }
        }
    }

    private void positionParagraph(XWPFParagraph paragraph, BoundingBox bounds, double previousBottom, double originX) {
        double gap = Math.max(0, bounds.top() - previousBottom);
        paragraph.setSpacingBefore((float) Math.min(240, gap));
        paragraph.setSpacingAfter(0);
        paragraph.setIndentationLeft((int) Math.max(0, Math.round((bounds.left() - originX) * 20)));
        paragraph.setAlignment(ParagraphAlignment.LEFT);
    }

    private void writeSpans(XWPFParagraph paragraph, List<TextSpan> spans) {
        for (TextSpan span : spans) {
            XWPFRun run = paragraph.createRun();
            String text = span.text();
            if (text != null) run.setText(text);
            float fontSize = (float) Math.max(MIN_FONT_PT, Math.min(MAX_FONT_PT, span.fontSize()));
            run.setFontSize(fontSize);
            if (span.fontFamily() != null && !span.fontFamily().isBlank()) {
                run.setFontFamily(normalizeFontFamily(span.fontFamily()));
            }
            run.setBold(span.bold());
            run.setItalic(span.italic());
            if (span.color() != null) run.setColor(span.color());
        }
    }

    private void writeImage(XWPFParagraph paragraph, ImageBlock image) throws IOException {
        if (image.data() == null || image.data().length == 0) return;
        int pictureType = pictureType(image.mimeType());
        double widthInches = Math.max(0.05, image.bounds().width() / POINTS_PER_INCH);
        double heightInches = Math.max(0.05, image.bounds().height() / POINTS_PER_INCH);
        try (InputStream input = new java.io.ByteArrayInputStream(image.data())) {
            paragraph.createRun().addPicture(input, pictureType, "pdf-image", Units.toEMU(widthInches), Units.toEMU(heightInches));
        } catch (InvalidFormatException e) {
            throw new IOException("Unable to embed PDF image", e);
        }
    }

    private void writeTableAfter(XWPFParagraph anchor, TableBlock table) {
        XmlCursor cursor = anchor.getCTP().newCursor();
        try {
            cursor.toEndToken();
            XWPFDocument document = anchor.getDocument();
            XWPFTable wordTable = document.insertNewTbl(cursor);
            wordTable.setWidth("100%");
            CTTblLayoutType layout = wordTable.getCTTbl().getTblPr().isSetTblLayout()
                ? wordTable.getCTTbl().getTblPr().getTblLayout() : wordTable.getCTTbl().getTblPr().addNewTblLayout();
            layout.setType(STTblLayoutType.FIXED);
            wordTable.getCTTbl().getTblPr().unsetTblBorders();
            int targetRows = table.rows().size();
            int targetCols = table.rows().stream().mapToInt(List::size).max().orElse(1);
            while (wordTable.getNumberOfRows() < targetRows) wordTable.createRow();
            while (wordTable.getRow(0).getTableCells().size() < targetCols) wordTable.getRow(0).addNewTableCell();
            for (int r = 0; r < targetRows; r++) {
                XWPFTableRow row = wordTable.getRow(r);
                while (row.getTableCells().size() < targetCols) row.addNewTableCell();
                List<TextBlock> sourceRow = table.rows().get(r);
                for (int c = 0; c < sourceRow.size(); c++) {
                    XWPFTableCell cell = row.getCell(c);
                    clearCell(cell);
                    XWPFParagraph paragraph = cell.addParagraph();
                    paragraph.setSpacingBefore(0);
                    paragraph.setSpacingAfter(0);
                    writeSpans(paragraph, sourceRow.get(c).spans());
                }
            }
        } finally {
            cursor.dispose();
        }
    }

    private void clearCell(XWPFTableCell cell) {
        while (cell.getParagraphs().size() > 1) cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.getParagraphs().getFirst();
        while (!paragraph.getRuns().isEmpty()) paragraph.removeRun(0);
    }

    private void addPageBreak(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setPageBreak(true);
    }

    private boolean isFullPageImage(ImageBlock image, PageModel page) {
        BoundingBox b = image.bounds();
        return b.width() >= page.width() * 0.95 && b.height() >= page.height() * 0.95;
    }

    private int pictureType(String mimeType) {
        if (mimeType == null) return Document.PICTURE_TYPE_PNG;
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> Document.PICTURE_TYPE_JPEG;
            case "image/gif" -> Document.PICTURE_TYPE_GIF;
            case "image/bmp" -> Document.PICTURE_TYPE_BMP;
            case "image/tiff" -> Document.PICTURE_TYPE_TIFF;
            case "image/emf" -> Document.PICTURE_TYPE_EMF;
            case "image/wmf" -> Document.PICTURE_TYPE_WMF;
            default -> Document.PICTURE_TYPE_PNG;
        };
    }

    private String normalizeFontFamily(String family) {
        String normalized = family.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.equalsIgnoreCase("TimesNewRomanPSMT")) return "Times New Roman";
        if (normalized.equalsIgnoreCase("ArialMT")) return "Arial";
        if (normalized.equalsIgnoreCase("SimSun")) return "宋体";
        if (normalized.equalsIgnoreCase("SimHei")) return "黑体";
        return normalized;
    }
}
