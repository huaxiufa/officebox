package com.officebox.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.util.Units;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PdfToWordService {
    public static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    public ResponseEntity<byte[]> convert(MultipartFile file) {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        String name = file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename();
        if (!name.toLowerCase().endsWith(".pdf")) return error("请选择 PDF 文件");
        Path input = null;
        try {
            input = Files.createTempFile("officebox-pdf-word-", ".pdf");
            Files.write(input, file.getBytes());
            try (PDDocument pdf = Loader.loadPDF(input.toFile()); XWPFDocument docx = new XWPFDocument()) {
                if (pdf.getNumberOfPages() == 0) return error("PDF 没有页面");
                boolean hasText = false;
                for (int pageNo = 1; pageNo <= pdf.getNumberOfPages(); pageNo++) {
                    PDPage page = pdf.getPage(pageNo - 1);
                    PositionStripper stripper = new PositionStripper();
                    stripper.setSortByPosition(true);
                    stripper.setStartPage(pageNo);
                    stripper.setEndPage(pageNo);
                    stripper.getText(pdf);
                    List<Line> lines = stripper.lines();
                    hasText |= !lines.isEmpty();
                    writePage(docx, page, lines, pageNo == 1);
                    if (pageNo < pdf.getNumberOfPages()) {
                        XWPFParagraph p = docx.createParagraph();
                        p.createRun().addBreak(BreakType.PAGE);
                    }
                }
                if (!hasText) return error("PDF 没有可提取的文字层；扫描版 OCR 将单独处理");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                docx.write(out);
                return ResponseEntity.ok().contentType(MediaType.parseMediaType(DOCX_TYPE))
                        .header("Content-Disposition", "attachment; filename=\"document.docx\"")
                        .body(out.toByteArray());
            }
        } catch (Exception e) {
            return error("PDF 转 Word 失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            if (input != null) try { Files.deleteIfExists(input); } catch (Exception ignored) { }
        }
    }

    private void writePage(XWPFDocument docx, PDPage page, List<Line> lines, boolean firstPage) throws Exception {
        var sectPr = docx.getDocument().getBody().getSectPr();
        if (sectPr.getPgSz() != null) {
            sectPr.getPgSz().setW(twips(page.getMediaBox().getWidth()));
            sectPr.getPgSz().setH(twips(page.getMediaBox().getHeight()));
        }
        if (sectPr.getPgMar() != null) {
            int m = twips(24);
            sectPr.getPgMar().setTop(m); sectPr.getPgMar().setBottom(m);
            sectPr.getPgMar().setLeft(m); sectPr.getPgMar().setRight(m);
        }

        double pageWidth = page.getMediaBox().getWidth();
        double split = findColumnSplit(lines, pageWidth);
        List<Line> left = new ArrayList<>(), right = new ArrayList<>();
        for (Line l : lines) {
            if (l.x < split) left.add(l); else right.add(l);
        }
        boolean twoColumns = !left.isEmpty() && !right.isEmpty() && split > 0 && split < pageWidth;
        if (!twoColumns) {
            for (Line l : lines) addLine(docx, l);
            return;
        }

        XWPFTable table = docx.createTable(1, 2);
        table.setWidth("100%");
        XWPFTableCell lc = table.getRow(0).getCell(0);
        XWPFTableCell rc = table.getRow(0).getCell(1);
        lc.setWidth("34%"); rc.setWidth("66%");
        lc.setColor("EFF6FB");
        clearCell(lc); clearCell(rc);
        if (firstPage) addEmbeddedImages(lc, page);
        for (Line l : left) addLine(lc, l);
        for (Line l : right) addLine(rc, l);
    }

    private static double findColumnSplit(List<Line> lines, double pageWidth) {
        if (lines.size() < 6) return pageWidth;
        List<Double> xs = new ArrayList<>();
        for (Line l : lines) xs.add(l.x);
        xs.sort(Double::compareTo);
        double bestGap = 0, split = pageWidth;
        for (int i = 1; i < xs.size(); i++) {
            double a = xs.get(i - 1), b = xs.get(i);
            if (b - a > bestGap && a > pageWidth * .18 && b < pageWidth * .82) {
                bestGap = b - a; split = (a + b) / 2;
            }
        }
        return bestGap >= 55 ? split : pageWidth;
    }

    private static void addLine(XWPFDocument docx, Line line) { addLine(docx.createParagraph(), line); }
    private static void addLine(XWPFTableCell cell, Line line) { addLine(cell.addParagraph(), line); }
    private static void addLine(XWPFParagraph p, Line line) {
        p.setSpacingBefore(0); p.setSpacingAfter(line.heading ? 3 : 1);
        for (Span s : line.spans) {
            XWPFRun r = p.createRun(); r.setText(s.text);
            r.setFontSize((float)Math.max(7, Math.min(24, s.size)));
            r.setBold(s.bold || line.heading); r.setItalic(s.italic);
        }
    }

    private static void clearCell(XWPFTableCell cell) {
        while (cell.getParagraphs().size() > 0) cell.removeParagraph(0);
        cell.addParagraph();
    }

    private static void addEmbeddedImages(XWPFTableCell cell, PDPage page) throws Exception {
        var resources = page.getResources();
        for (var name : resources.getXObjectNames()) {
            if (!resources.isImageXObject(name)) continue;
            var image = (org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) resources.getXObject(name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image.getImage(), "png", out);
            XWPFParagraph p = cell.addParagraph(); p.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun r = p.createRun();
            int size = Math.min(110, Math.max(55, image.getWidth()));
            r.addPicture(new ByteArrayInputStream(out.toByteArray()), Document.PICTURE_TYPE_PNG, "pdf-image.png", Units.toEMU(size), Units.toEMU(size));
        }
    }

    private static int twips(double pt) { return (int)Math.max(0, Math.min(Integer.MAX_VALUE, Math.round(pt * 20))); }
    private static ResponseEntity<byte[]> error(String message) {
        return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN).body(message.getBytes(StandardCharsets.UTF_8));
    }

    private static final class PositionStripper extends PDFTextStripper {
        private final List<Glyph> glyphs = new ArrayList<>();
        PositionStripper() throws java.io.IOException { super(); }
        @Override protected void writeString(String text, List<TextPosition> positions) throws java.io.IOException {
            for (TextPosition p : positions) {
                String u = p.getUnicode(); if (u == null || u.isEmpty()) continue;
                glyphs.add(new Glyph(u, p.getXDirAdj(), p.getYDirAdj(), p.getFontSizeInPt(), p.getFont() == null ? "" : p.getFont().getName()));
            }
        }
        List<Line> lines() {
            glyphs.sort(Comparator.comparingDouble((Glyph g)->g.y).thenComparingDouble(g->g.x));
            List<Line> out = new ArrayList<>();
            for (Glyph g : glyphs) {
                Line target = null;
                for (int i=out.size()-1;i>=0;i--) {
                    Line l=out.get(i);
                    if (Math.abs(l.y-g.y)<=Math.max(2.5,g.size*.45)) { target=l; break; }
                    if (l.y<g.y-9) break;
                }
                if(target==null){target=new Line(g.y,g.x);out.add(target);} target.glyphs.add(g); target.x=Math.min(target.x,g.x);
            }
            out.sort(Comparator.comparingDouble(l->l.y)); for(Line l:out)l.finish(); return out;
        }
    }
    private static final class Glyph { final String text,font; final double x,y,size; Glyph(String t,double x,double y,double s,String f){text=t;this.x=x;this.y=y;size=s;font=f;} }
    private static final class Line {
        final double y; double x; final List<Glyph> glyphs=new ArrayList<>(); final List<Span> spans=new ArrayList<>(); boolean heading;
        Line(double y,double x){this.y=y;this.x=x;}
        void finish(){
            glyphs.sort(Comparator.comparingDouble(g->g.x)); Span cur=null;
            for(Glyph g:glyphs){boolean b=g.font.toLowerCase().contains("bold")||g.font.toLowerCase().contains("black"); boolean it=g.font.toLowerCase().contains("italic")||g.font.toLowerCase().contains("oblique");
                if(cur==null||Math.abs(cur.size-g.size)>.5||cur.bold!=b||cur.italic!=it){cur=new Span(g.text,g.size,b,it);spans.add(cur);}else cur.text+=g.text;}
            double avg=spans.stream().mapToDouble(s->s.size).average().orElse(10);
            heading=avg>=13 || (spans.stream().mapToInt(s->s.text.length()).sum()<=28 && spans.stream().anyMatch(s->s.bold));
        }
    }
    private static final class Span { String text; final double size; final boolean bold,italic; Span(String t,double s,boolean b,boolean i){text=t;size=s;bold=b;italic=i;} }
}
