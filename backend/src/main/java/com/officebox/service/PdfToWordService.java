package com.officebox.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
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
                PDFRenderer renderer = new PDFRenderer(pdf);
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
                    writePage(docx, page, lines, pageNo == 1, renderer.renderImageWithDPI(pageNo - 1, 110));
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

    private void writePage(XWPFDocument docx, PDPage page, List<Line> lines, boolean firstPage, java.awt.image.BufferedImage rendered) throws Exception {
        CTSectPr sectPr = docx.getDocument().getBody().isSetSectPr()
                ? docx.getDocument().getBody().getSectPr() : docx.getDocument().getBody().addNewSectPr();
        CTPageSz pgSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pgSz.setW(twips(page.getMediaBox().getWidth()));
        pgSz.setH(twips(page.getMediaBox().getHeight()));
        var pgMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        int m = twips(18); pgMar.setTop(m); pgMar.setBottom(m); pgMar.setLeft(m); pgMar.setRight(m);

        // Keep the original page as a visual reference. This is intentionally placed
        // before the editable reconstruction so users can compare the conversion.
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        ImageIO.write(rendered, "png", image);
        XWPFParagraph visual = docx.createParagraph();
        visual.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun visualRun = visual.createRun();
        int widthEmu = Units.toEMU(Math.max(1, page.getMediaBox().getWidth() / 72.0 * 96.0));
        int heightEmu = Units.toEMU(Math.max(1, page.getMediaBox().getHeight() / 72.0 * 96.0));
        visualRun.addPicture(new ByteArrayInputStream(image.toByteArray()), Document.PICTURE_TYPE_PNG, "pdf-page.png", widthEmu, heightEmu);

        // Editable reconstruction follows the visual page. This deliberately avoids
        // duplicating PDF text on top of the image, which would make the output unreadable.
        XWPFParagraph marker = docx.createParagraph();
        marker.setSpacingAfter(0);
        XWPFRun markerRun = marker.createRun();
        markerRun.setText("Editable text for this page");
        markerRun.setBold(true);
        markerRun.setFontSize(8);
        for (Line l : lines) addLine(docx, l);
    }

    private static void addLine(XWPFDocument d, Line l){ addLine(d.createParagraph(), l); }
    private static void addLine(XWPFParagraph p, Line l){
        p.setSpacingBefore(0); p.setSpacingAfter(l.heading ? 3 : 1);
        for (Span s : l.spans) { XWPFRun r=p.createRun(); r.setText(s.text); r.setFontSize((float)Math.max(7,Math.min(24,s.size))); r.setBold(s.bold||l.heading); r.setItalic(s.italic); }
    }
    private static int twips(double pt){ return (int)Math.max(0,Math.min(Integer.MAX_VALUE,Math.round(pt*20))); }
    private static ResponseEntity<byte[]> error(String m){ return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN).body(m.getBytes(StandardCharsets.UTF_8)); }

    private static final class PositionStripper extends PDFTextStripper {
        private final List<Glyph> glyphs=new ArrayList<>(); PositionStripper() throws java.io.IOException { super(); }
        @Override protected void writeString(String text,List<TextPosition> positions)throws java.io.IOException { for(TextPosition p:positions){String u=p.getUnicode();if(u==null||u.isEmpty())continue;glyphs.add(new Glyph(u,p.getXDirAdj(),p.getYDirAdj(),p.getFontSizeInPt(),p.getFont()==null?"":p.getFont().getName()));} }
        List<Line> lines(){ glyphs.sort(Comparator.comparingDouble((Glyph g)->g.y).thenComparingDouble(g->g.x)); List<Line> out=new ArrayList<>(); for(Glyph g:glyphs){Line t=null;for(int i=out.size()-1;i>=0;i--){Line l=out.get(i);if(Math.abs(l.y-g.y)<=Math.max(2.5,g.size*.45)){t=l;break;}if(l.y<g.y-9)break;}if(t==null){t=new Line(g.y,g.x);out.add(t);}t.glyphs.add(g);t.x=Math.min(t.x,g.x);}out.sort(Comparator.comparingDouble(l->l.y));for(Line l:out)l.finish();return out; }
    }
    private static final class Glyph{final String text,font;final double x,y,size;Glyph(String t,double x,double y,double s,String f){text=t;this.x=x;this.y=y;size=s;font=f;}}
    private static final class Line{final double y;double x;final List<Glyph> glyphs=new ArrayList<>();final List<Span> spans=new ArrayList<>();boolean heading;Line(double y,double x){this.y=y;this.x=x;}void finish(){glyphs.sort(Comparator.comparingDouble(g->g.x));Span cur=null;for(Glyph g:glyphs){boolean b=g.font.toLowerCase().contains("bold")||g.font.toLowerCase().contains("black");boolean it=g.font.toLowerCase().contains("italic")||g.font.toLowerCase().contains("oblique");if(cur==null||Math.abs(cur.size-g.size)>.5||cur.bold!=b||cur.italic!=it){cur=new Span(g.text,g.size,b,it);spans.add(cur);}else cur.text+=g.text;}double avg=spans.stream().mapToDouble(s->s.size).average().orElse(10);heading=avg>=13||(spans.stream().mapToInt(s->s.text.length()).sum()<=28&&spans.stream().anyMatch(s->s.bold));}}
    private static final class Span{String text;final double size;final boolean bold,italic;Span(String t,double s,boolean b,boolean i){text=t;size=s;bold=b;italic=i;}}
}
