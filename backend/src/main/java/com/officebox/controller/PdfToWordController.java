package com.officebox.controller;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.util.Units;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;

@RestController
@RequestMapping("/api/pdf")
public class PdfToWordController {
    private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final double PDF_MARGIN_PT = 28.0;
    private static final double HEADER_END_Y_PT = 180.0;

    @PostMapping(value = "/to-word", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> toWord(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        String original = file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename();
        if (!original.toLowerCase().endsWith(".pdf")) return error("请选择 PDF 文件");

        Path input = null;
        try {
            input = Files.createTempFile("officebox-pdf-word-", ".pdf");
            Files.write(input, file.getBytes());

            try (PDDocument pdf = Loader.loadPDF(input.toFile()); XWPFDocument docx = new XWPFDocument()) {
                if (pdf.getNumberOfPages() == 0) return error("PDF 没有页面");

                configurePage(docx, pdf.getPage(0));
                boolean hasText = false;

                for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                    PDPage pdfPage = pdf.getPage(page - 1);
                    PositionStripper stripper = new PositionStripper();
                    stripper.setSortByPosition(true);
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    stripper.getText(pdf);
                    List<Line> lines = stripper.lines();
                    if (!lines.isEmpty()) hasText = true;

                    if (page == 1 && hasHeaderContent(lines, pdfPage)) {
                        addHeader(docx, lines, pdfPage);
                        for (Line line : lines) {
                            if (line.y < HEADER_END_Y_PT) line.renderedInHeader = true;
                        }
                    }

                    for (Line line : lines) {
                        if (line.renderedInHeader) continue;
                        XWPFParagraph paragraph = docx.createParagraph();
                        paragraph.setSpacingAfter(0);
                        paragraph.setSpacingBefore(0);
                        paragraph.setIndentationLeft(toTwips(Math.max(0, line.x - PDF_MARGIN_PT)));

                        for (Span span : line.spans) {
                            XWPFRun run = paragraph.createRun();
                            run.setText(span.text);
                            float size = (float) Math.max(7d, Math.min(48d, span.fontSize));
                            run.setFontSize(size);
                            if (span.bold) run.setBold(true);
                            if (span.italic) run.setItalic(true);
                        }
                    }

                    if (page < pdf.getNumberOfPages()) {
                        XWPFParagraph pageBreak = docx.createParagraph();
                        pageBreak.createRun().addBreak(BreakType.PAGE);
                    }
                }

                if (!hasText) {
                    return error("PDF 没有可提取的文字层；当前版本需要 PDF 内含文字层，扫描版 OCR 转换将单独处理");
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                docx.write(out);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(DOCX_TYPE))
                        .header("Content-Disposition", "attachment; filename=\"document.docx\"")
                        .body(out.toByteArray());
            }
        } catch (Exception e) {
            return error("PDF 转 Word 失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            if (input != null) try { Files.deleteIfExists(input); } catch (Exception ignored) { }
        }
    }

    private static void configurePage(XWPFDocument docx, PDPage page) {
        var sectPr = docx.getDocument().getBody().getSectPr();
        var pgSz = sectPr.getPgSz();
        if (pgSz != null) {
            pgSz.setW(toTwips(page.getMediaBox().getWidth()));
            pgSz.setH(toTwips(page.getMediaBox().getHeight()));
        }
        var pgMar = sectPr.getPgMar();
        if (pgMar != null) {
            int margin = toTwips(PDF_MARGIN_PT);
            pgMar.setTop(margin);
            pgMar.setBottom(margin);
            pgMar.setLeft(margin);
            pgMar.setRight(margin);
        }
    }

    private static int toTwips(double points) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.round(points * 20.0)));
    }

    private static boolean hasHeaderContent(List<Line> lines, PDPage page) {
        if (lines.stream().anyMatch(l -> l.y < HEADER_END_Y_PT)) return true;
        try {
            PDResources resources = page.getResources();
            for (COSName name : resources.getXObjectNames()) {
                if (resources.isImageXObject(name)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private static void addHeader(XWPFDocument docx, List<Line> lines, PDPage page) throws Exception {
        XWPFTable table = docx.createTable(1, 2);
        table.setWidth("100%");
        XWPFTableCell photoCell = table.getCell(0, 0);
        XWPFTableCell infoCell = table.getCell(0, 1);
        photoCell.setColor("EFF6FB");
        infoCell.setColor("EFF6FB");

        EmbeddedImages images = extractImages(page);
        if (images.photo != null) {
            XWPFParagraph p = photoCell.getParagraphs().get(0);
            p.setSpacingAfter(0);
            XWPFRun r = p.createRun();
            r.addPicture(new ByteArrayInputStream(images.photo), Document.PICTURE_TYPE_PNG, "photo.png", Units.toEMU(72), Units.toEMU(72));
        }

        if (images.logo != null) {
            XWPFParagraph p = infoCell.getParagraphs().get(0);
            p.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
            p.setSpacingAfter(0);
            XWPFRun r = p.createRun();
            r.addPicture(new ByteArrayInputStream(images.logo), Document.PICTURE_TYPE_PNG, "logo.png", Units.toEMU(145), Units.toEMU(82));
        }

        List<Line> headerLines = new ArrayList<>();
        for (Line line : lines) if (line.y < HEADER_END_Y_PT) headerLines.add(line);
        headerLines.sort(Comparator.comparingDouble(l -> l.y));
        for (Line line : headerLines) {
            XWPFParagraph p = infoCell.addParagraph();
            p.setSpacingBefore(0);
            p.setSpacingAfter(0);
            p.setIndentationLeft(0);
            for (Span span : line.spans) {
                XWPFRun r = p.createRun();
                r.setText(span.text);
                r.setFontSize((float) Math.max(7d, Math.min(24d, span.fontSize)));
                if (span.bold) r.setBold(true);
                if (span.italic) r.setItalic(true);
            }
        }

        // Remove default empty paragraph left by the table cell before the generated header content.
        if (infoCell.getParagraphs().size() > 1 && infoCell.getParagraphs().get(0).getText().isEmpty() && images.logo == null) {
            infoCell.removeParagraph(0);
        }
    }

    private static EmbeddedImages extractImages(PDPage page) {
        EmbeddedImages result = new EmbeddedImages();
        try {
            PDResources resources = page.getResources();
            for (COSName name : resources.getXObjectNames()) {
                if (!resources.isImageXObject(name)) continue;
                PDImageXObject image = (PDImageXObject) resources.getXObject(name);
                BufferedImage buffered = image.getImage();
                if (buffered == null) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(buffered, "png", out);
                byte[] png = out.toByteArray();
                if (image.getWidth() <= 500 && image.getHeight() <= 500 && result.photo == null) {
                    result.photo = png;
                } else if (result.logo == null) {
                    result.logo = png;
                }
            }
        } catch (Exception ignored) { }
        return result;
    }

    private static ResponseEntity<byte[]> error(String message) {
        return ResponseEntity.internalServerError()
                .contentType(MediaType.TEXT_PLAIN)
                .body(message.getBytes(StandardCharsets.UTF_8));
    }

    private static final class EmbeddedImages {
        byte[] photo;
        byte[] logo;
    }

    private static final class PositionStripper extends PDFTextStripper {
        private final List<Glyph> glyphs = new ArrayList<>();

        PositionStripper() throws java.io.IOException { super(); }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws java.io.IOException {
            for (TextPosition p : positions) {
                String unicode = p.getUnicode();
                if (unicode == null || unicode.isEmpty()) continue;
                glyphs.add(new Glyph(unicode, p.getXDirAdj(), p.getYDirAdj(), p.getWidthDirAdj(), p.getHeightDir(), p.getFontSizeInPt(), p.getFont() == null ? "" : p.getFont().getName()));
            }
        }

        List<Line> lines() {
            glyphs.sort(Comparator.comparingDouble((Glyph g) -> g.y).thenComparingDouble(g -> g.x));
            List<Line> result = new ArrayList<>();
            for (Glyph glyph : glyphs) {
                Line line = null;
                for (int i = result.size() - 1; i >= 0; i--) {
                    Line candidate = result.get(i);
                    if (Math.abs(candidate.y - glyph.y) <= Math.max(2.5, glyph.height * 0.45)) {
                        line = candidate;
                        break;
                    }
                    if (candidate.y < glyph.y - 8) break;
                }
                if (line == null) {
                    line = new Line(glyph.y, glyph.x);
                    result.add(line);
                }
                line.add(glyph);
            }
            result.sort(Comparator.comparingDouble(l -> l.y));
            for (Line line : result) line.finish();
            return result;
        }
    }

    private static final class Line {
        final double y;
        double x;
        boolean renderedInHeader;
        final List<Glyph> glyphs = new ArrayList<>();
        final List<Span> spans = new ArrayList<>();

        Line(double y, double x) { this.y = y; this.x = x; }

        void add(Glyph glyph) { glyphs.add(glyph); x = Math.min(x, glyph.x); }

        void finish() {
            glyphs.sort(Comparator.comparingDouble(g -> g.x));
            Span current = null;
            for (Glyph glyph : glyphs) {
                boolean bold = glyph.font.toLowerCase().contains("bold") || glyph.font.toLowerCase().contains("black");
                boolean italic = glyph.font.toLowerCase().contains("italic") || glyph.font.toLowerCase().contains("oblique");
                if (current == null || Math.abs(current.fontSize - glyph.fontSize) > 0.5 || current.bold != bold || current.italic != italic) {
                    current = new Span(glyph.text, glyph.fontSize, bold, italic);
                    spans.add(current);
                } else {
                    current.text += glyph.text;
                }
            }
        }
    }

    private static final class Glyph {
        final String text;
        final double x, y, width, height, fontSize;
        final String font;

        Glyph(String text, double x, double y, double width, double height, double fontSize, String font) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.fontSize = fontSize;
            this.font = font;
        }
    }

    private static final class Span {
        String text;
        final double fontSize;
        final boolean bold;
        final boolean italic;

        Span(String text, double fontSize, boolean bold, boolean italic) {
            this.text = text;
            this.fontSize = fontSize;
            this.bold = bold;
            this.italic = italic;
        }
    }
}
