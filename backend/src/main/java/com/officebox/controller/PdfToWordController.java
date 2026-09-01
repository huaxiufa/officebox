package com.officebox.controller;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/pdf")
public class PdfToWordController {
    private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

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

                boolean hasText = false;
                for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                    PositionStripper stripper = new PositionStripper();
                    stripper.setSortByPosition(true);
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    stripper.getText(pdf);
                    List<Line> lines = stripper.lines();
                    if (!lines.isEmpty()) hasText = true;

                    for (Line line : lines) {
                        XWPFParagraph paragraph = docx.createParagraph();
                        paragraph.setSpacingAfter(0);
                        paragraph.setSpacingBefore(0);
                        // Keep the horizontal placement approximately where it was in the PDF.
                        if (line.x > 1) paragraph.setIndentationLeft((int) Math.round(line.x));

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

    private static ResponseEntity<byte[]> error(String message) {
        return ResponseEntity.internalServerError()
                .contentType(MediaType.TEXT_PLAIN)
                .body(message.getBytes(StandardCharsets.UTF_8));
    }

    private static final class PositionStripper extends PDFTextStripper {
        private final List<Glyph> glyphs = new ArrayList<>();

        PositionStripper() throws java.io.IOException {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws java.io.IOException {
            for (TextPosition p : positions) {
                String unicode = p.getUnicode();
                if (unicode == null || unicode.isEmpty()) continue;
                glyphs.add(new Glyph(
                        unicode,
                        p.getXDirAdj(),
                        p.getYDirAdj(),
                        p.getWidthDirAdj(),
                        p.getHeightDir(),
                        p.getFontSizeInPt(),
                        p.getFont() == null ? "" : p.getFont().getName()));
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
        final List<Glyph> glyphs = new ArrayList<>();
        final List<Span> spans = new ArrayList<>();

        Line(double y, double x) { this.y = y; this.x = x; }

        void add(Glyph glyph) {
            glyphs.add(glyph);
            x = Math.min(x, glyph.x);
        }

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
