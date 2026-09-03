package com.officebox.common.conversion;

import com.officebox.common.model.ImageBlock;
import com.officebox.common.model.PageBlock;
import com.officebox.common.model.PageModel;
import com.officebox.common.model.TextBlock;
import com.officebox.common.model.TextSpan;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Renders PDF-derived page models into a visually structured DOCX document.
 * The public render API is intentionally kept stable for existing callers.
 */
public class DocxRenderer {
    private static final String ACCENT = "1F4E79";
    private static final String TEXT = "222222";
    private static final String MUTED = "666666";
    private static final String DEFAULT_FONT = "Aptos";
    private static final String CJK_FONT = "等线";

    public Path render(List<PageModel> pages, Path output) throws IOException {
        if (pages == null || pages.isEmpty()) {
            throw new IllegalArgumentException("pages must not be empty");
        }
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }

        try (XWPFDocument document = new XWPFDocument()) {
            addFooter(document);
            for (int i = 0; i < pages.size(); i++) {
                if (i > 0) {
                    document.createParagraph().setPageBreak(true);
                }
                renderPage(document, pages.get(i));
            }
            try (var out = Files.newOutputStream(output)) {
                document.write(out);
            }
        }
        return output;
    }

    private void addFooter(XWPFDocument document) {
        XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(document);
        XWPFFooter footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT);
        XWPFParagraph p = footer.getParagraphArray(0);
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingBefore(0);
        p.setSpacingAfter(0);
        XWPFRun r = p.createRun();
        r.setFontFamily(DEFAULT_FONT);
        r.setFontSize(8);
        r.setColor(MUTED);
        r.setText("OfficeBox  •  ");
        r = p.createRun();
        r.setFontFamily(DEFAULT_FONT);
        r.setFontSize(8);
        r.setColor(MUTED);
        r.getCTR().addNewFldChar().setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.BEGIN);
        r.getCTR().addNewInstrText().setStringValue(" PAGE ");
        r.getCTR().addNewFldChar().setFldCharType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.END);
    }

    private void renderPage(XWPFDocument document, PageModel page) throws IOException {
        configurePage(document, page);
        List<PageBlock> blocks = new ArrayList<>(page.getBlocks());
        blocks.sort(Comparator.comparingDouble(PageBlock::getY).thenComparingDouble(PageBlock::getX));

        List<ImageBlock> fullPageImages = new ArrayList<>();
        List<TextBlock> texts = new ArrayList<>();
        for (PageBlock block : blocks) {
            if (block instanceof ImageBlock image) {
                if (isFullPageImage(image, page)) fullPageImages.add(image);
            } else if (block instanceof TextBlock text) {
                texts.add(text);
            }
        }

        if (fullPageImages.isEmpty()) {
            if (hasTwoColumns(texts, page)) renderTwoColumns(document, texts, page);
            else renderSingleColumn(document, texts);
        } else {
            for (PageBlock block : blocks) {
                if (block instanceof ImageBlock image && fullPageImages.contains(image)) writeImage(document, image);
                else if (block instanceof TextBlock text) writeTextBlock(document, text);
            }
        }
    }

    private void configurePage(XWPFDocument document, PageModel page) {
        XWPFDocument doc = document;
        XWPFParagraph first = doc.getParagraphs().isEmpty() ? doc.createParagraph() : doc.getParagraphs().get(0);
        var section = doc.getDocument().getBody().getSectPr();
        if (section == null) section = doc.getDocument().getBody().addNewSectPr();
        CTPageSz sz = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        sz.setW(toTwips(page.getWidth()));
        sz.setH(toTwips(page.getHeight()));
        CTPageMar mar = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        mar.setTop(toTwips(36));
        mar.setBottom(toTwips(36));
        mar.setLeft(toTwips(42));
        mar.setRight(toTwips(42));
        mar.setHeader(toTwips(18));
        mar.setFooter(toTwips(18));
        first.setSpacingAfter(0);
    }

    private void renderSingleColumn(XWPFDocument document, List<TextBlock> texts) {
        for (TextBlock text : texts) writeTextBlock(document, text);
    }

    private void renderTwoColumns(XWPFDocument document, List<TextBlock> texts, PageModel page) {
        double mid = page.getWidth() / 2.0;
        List<TextBlock> left = new ArrayList<>();
        List<TextBlock> right = new ArrayList<>();
        for (TextBlock text : texts) {
            if (text.getX() + text.getWidth() / 2.0 < mid) left.add(text);
            else right.add(text);
        }
        XWPFTable table = document.createTable(1, 2);
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);
        setBorderless(table);
        setCellWidth(table.getCell(0, 0), "45%");
        setCellWidth(table.getCell(0, 1), "55%");
        renderCell(table.getCell(0, 0), left);
        renderCell(table.getCell(0, 1), right);
    }

    private void renderCell(XWPFTableCell cell, List<TextBlock> texts) {
        XWPFParagraph initial = cell.getParagraphs().get(0);
        cell.removeParagraph(0);
        for (TextBlock text : texts) {
            XWPFParagraph p = cell.addParagraph();
            styleParagraph(p, text);
            writeRuns(p, text);
        }
        if (texts.isEmpty()) cell.addParagraph();
        else initial.setSpacingAfter(0);
    }

    private void writeTextBlock(XWPFDocument document, TextBlock text) {
        XWPFParagraph p = document.createParagraph();
        styleParagraph(p, text);
        writeRuns(p, text);
    }

    private void styleParagraph(XWPFParagraph p, TextBlock text) {
        boolean heading = isHeading(text);
        p.setSpacingBefore(heading ? 7 : 0);
        p.setSpacingAfter(heading ? 3 : 2);
        p.setKeepNext(heading);
        if (text.getText() != null && text.getText().trim().matches("^[•●▪◦*-]\\s+.*")) {
            p.setIndentationLeft(300);
            p.setIndentationHanging(180);
        }
    }

    private void writeRuns(XWPFParagraph p, TextBlock text) {
        List<TextSpan> spans = text.getSpans();
        if (spans == null || spans.isEmpty()) {
            XWPFRun run = p.createRun();
            applyRunStyle(run, text, text.getText() == null ? "" : text.getText());
            return;
        }
        for (TextSpan span : spans) {
            XWPFRun run = p.createRun();
            run.setText(span.getText() == null ? "" : span.getText());
            run.setBold(span.isBold());
            run.setItalic(span.isItalic());
            run.setFontSize(Math.max(8, Math.min(24, span.getFontSize() > 0 ? span.getFontSize() : 10)));
            run.setFontFamily(DEFAULT_FONT);
            run.setColor(isHeading(text) ? ACCENT : TEXT);
            run.setFontFamily(CJK_FONT, XWPFRun.FontCharRange.EAST_ASIA);
        }
    }

    private void applyRunStyle(XWPFRun run, TextBlock text, String value) {
        boolean heading = isHeading(text);
        run.setText(value);
        run.setFontFamily(DEFAULT_FONT);
        run.setFontSize(heading ? 14 : 10);
        run.setBold(heading);
        run.setColor(heading ? ACCENT : TEXT);
        run.setFontFamily(CJK_FONT);
    }

    private void writeImage(XWPFDocument document, ImageBlock image) throws IOException {
        if (image.getData() == null || image.getData().length == 0) return;
        XWPFParagraph p = document.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        try (InputStream in = new java.io.ByteArrayInputStream(image.getData())) {
            run.addPicture(in, pictureType(image.getFormat()), "image", Units.toEMU(image.getWidth()), Units.toEMU(image.getHeight()));
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new IOException("Unsupported image format: " + image.getFormat(), e);
        }
    }

    private boolean isHeading(TextBlock text) {
        String s = text.getText() == null ? "" : text.getText().trim();
        return s.length() <= 50 && (s.equals(s.toUpperCase(Locale.ROOT)) || s.endsWith(":"));
    }

    private boolean isFullPageImage(ImageBlock image, PageModel page) {
        return image.getWidth() >= page.getWidth() * 0.85 && image.getHeight() >= page.getHeight() * 0.85;
    }

    private boolean hasTwoColumns(List<TextBlock> texts, PageModel page) {
        if (texts.size() < 4) return false;
        double mid = page.getWidth() / 2.0;
        int left = 0, right = 0;
        for (TextBlock t : texts) {
            if (t.getX() + t.getWidth() / 2.0 < mid) left++;
            else right++;
        }
        return left >= 2 && right >= 2;
    }

    private void setCellWidth(XWPFTableCell cell, String width) {
        cell.setWidth(width);
    }

    private void setBorderless(XWPFTable table) {
        var borders = table.getCTTbl().getTblPr().isSetTblBorders()
                ? table.getCTTbl().getTblPr().getTblBorders()
                : table.getCTTbl().getTblPr().addNewTblBorders();
        borders.addNewTop().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);
        borders.addNewLeft().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);
        borders.addNewBottom().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);
        borders.addNewRight().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);
        borders.addNewInsideH().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);
        borders.addNewInsideV().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);
    }

    private int pictureType(String format) {
        if (format == null) return Document.PICTURE_TYPE_PNG;
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> Document.PICTURE_TYPE_JPEG;
            case "gif" -> Document.PICTURE_TYPE_GIF;
            case "bmp" -> Document.PICTURE_TYPE_BMP;
            case "emf" -> Document.PICTURE_TYPE_EMF;
            case "wmf" -> Document.PICTURE_TYPE_WMF;
            default -> Document.PICTURE_TYPE_PNG;
        };
    }

    private long toTwips(double points) {
        return Math.round(points * 20.0);
    }
}
