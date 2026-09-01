package com.officebox.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PdfToWordService {
    public static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final double COLUMN_GAP_MIN_PT = 42.0;

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
                for (int pageNo = 0; pageNo < pdf.getNumberOfPages(); pageNo++) {
                    PDPage page = pdf.getPage(pageNo);
                    PositionStripper stripper = new PositionStripper();
                    stripper.setSortByPosition(true);
                    stripper.setStartPage(pageNo + 1);
                    stripper.setEndPage(pageNo + 1);
                    stripper.getText(pdf);
                    PageModel model = new PageModel(page.getMediaBox().getWidth(), page.getMediaBox().getHeight(), stripper.lines(), extractImages(page));
                    hasText |= !model.lines.isEmpty();
                    writePage(docx, page, model);
                    if (pageNo < pdf.getNumberOfPages() - 1) {
                        XWPFParagraph pageBreak = docx.createParagraph();
                        pageBreak.createRun().addBreak(BreakType.PAGE);
                    }
                }
                if (!hasText) return error("PDF 没有可提取的文字层；扫描版 OCR 将单独处理");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                docx.write(out);
                return ResponseEntity.ok().contentType(MediaType.parseMediaType(DOCX_TYPE))
                        .header("Content-Disposition", "attachment; filename=\"document.docx\"").body(out.toByteArray());
            }
        } catch (Exception e) {
            return error("PDF 转 Word 失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            if (input != null) try { Files.deleteIfExists(input); } catch (Exception ignored) { }
        }
    }

    private void writePage(XWPFDocument docx, PDPage page, PageModel model) throws Exception {
        configurePage(docx, page);
        double split = findColumnSplit(model.lines, model.width);
        if (split > 0) {
            List<Line> left = new ArrayList<>(), right = new ArrayList<>();
            for (Line line : model.lines) { if (line.x < split) left.add(line); else right.add(line); }
            if (!left.isEmpty() && !right.isEmpty()) {
                XWPFTable table = docx.createTable(1, 2);
                table.setWidth("100%"); removeTableBorders(table);
                XWPFTableRow row = table.getRow(0); XWPFTableCell lc = row.getCell(0), rc = row.getCell(1);
                setCellWidth(lc, split / model.width); setCellWidth(rc, 1.0 - split / model.width);
                clearCell(lc); clearCell(rc);
                addImagesAboveText(lc, left, model.images);
                addImagesAboveText(rc, right, model.images);
                for (Line line : left) addLine(lc, line, 0);
                for (Line line : right) addLine(rc, line, split);
                addImagesBelowText(lc, left, model.images);
                addImagesBelowText(rc, right, model.images);
                return;
            }
        }
        addImagesAboveText(docx, model.lines, model.images);
        for (Line line : model.lines) addLine(docx, line, 0);
        addImagesBelowText(docx, model.lines, model.images);
    }

    private static void configurePage(XWPFDocument docx, PDPage page) {
        CTSectPr sectPr = docx.getDocument().getBody().isSetSectPr() ? docx.getDocument().getBody().getSectPr() : docx.getDocument().getBody().addNewSectPr();
        CTPageSz size = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        size.setW(twips(page.getMediaBox().getWidth())); size.setH(twips(page.getMediaBox().getHeight()));
        CTPageMar margins = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        int margin = twips(18); margins.setTop(margin); margins.setBottom(margin); margins.setLeft(margin); margins.setRight(margin);
    }

    private static void addLine(XWPFTableCell cell, Line line, double split) { addLine(cell.addParagraph(), line, split); }
    private static void addLine(XWPFDocument docx, Line line, double split) { addLine(docx.createParagraph(), line, split); }
    private static void addLine(XWPFParagraph paragraph, Line line, double split) {
        paragraph.setSpacingBefore(0); paragraph.setSpacingAfter((int)Math.max(0, Math.min(18, line.afterGap)));
        paragraph.setIndentationLeft(twips(Math.max(0, line.x - split))); if (line.heading) paragraph.setKeepNext(true);
        for (Span span : line.spans) {
            XWPFRun run = paragraph.createRun(); run.setText(span.text); run.setFontSize((float)Math.max(6, Math.min(48, span.size)));
            run.setBold(span.bold || line.heading); run.setItalic(span.italic); if (!span.font.isBlank()) run.setFontFamily(normalizeFont(span.font));
        }
    }

    private static String normalizeFont(String font) { String value=font.replaceAll("[-,].*$", "").trim(); return value.isBlank()||value.contains("+")?"Arial":value.length()>80?value.substring(0,80):value; }
    private static double findColumnSplit(List<Line> lines,double pageWidth){
        if(lines.size()<8)return -1; List<Double> xs=new ArrayList<>(); for(Line l:lines)xs.add(l.x); xs.sort(Double::compareTo); double best=0,split=-1;
        for(int i=1;i<xs.size();i++){double a=xs.get(i-1),b=xs.get(i);if(a<pageWidth*.15||b>pageWidth*.85)continue;if(b-a>best){best=b-a;split=(a+b)/2;}} return best>=COLUMN_GAP_MIN_PT?split:-1;
    }

    private static List<EmbeddedImage> extractImages(PDPage page) throws IOException {
        ImageLocationExtractor extractor = new ImageLocationExtractor(page);
        extractor.processPage(page);
        return extractor.images();
    }

    private static void addImagesAboveText(XWPFDocument docx, List<Line> lines, List<EmbeddedImage> images) throws Exception {
        if (lines.isEmpty()) { for (EmbeddedImage image : images) addImage(docx.createParagraph(), image); return; }
        double firstY = lines.stream().mapToDouble(l -> l.y).min().orElse(Double.MAX_VALUE);
        for (EmbeddedImage image : images) if (image.y < firstY - 8) addImage(docx.createParagraph(), image);
    }

    private static void addImagesAboveText(XWPFTableCell cell, List<Line> lines, List<EmbeddedImage> images) throws Exception {
        if (lines.isEmpty()) { for (EmbeddedImage image : images) addImage(cell.addParagraph(), image); return; }
        double firstY = lines.stream().mapToDouble(l -> l.y).min().orElse(Double.MAX_VALUE);
        for (EmbeddedImage image : images) if (image.y < firstY - 8) addImage(cell.addParagraph(), image);
    }

    private static void addImagesBelowText(XWPFDocument docx, List<Line> lines, List<EmbeddedImage> images) throws Exception {
        double lastY = lines.stream().mapToDouble(l -> l.y).max().orElse(Double.MIN_VALUE);
        for (EmbeddedImage image : images) if (image.y >= lastY - 8) addImage(docx.createParagraph(), image);
    }

    private static void addImagesBelowText(XWPFTableCell cell, List<Line> lines, List<EmbeddedImage> images) throws Exception {
        double lastY = lines.stream().mapToDouble(l -> l.y).max().orElse(Double.MIN_VALUE);
        for (EmbeddedImage image : images) if (image.y >= lastY - 8) addImage(cell.addParagraph(), image);
    }

    private static void addImage(XWPFParagraph paragraph, EmbeddedImage image) throws Exception {
        XWPFRun run = paragraph.createRun();
        int width = Math.min(420, Math.max(32, (int)Math.round(image.width)));
        int height = Math.max(24, (int)Math.round(image.height * (width / Math.max(1.0, image.width))));
        int type = switch(image.format) { case "jpg", "jpeg" -> Document.PICTURE_TYPE_JPEG; case "gif" -> Document.PICTURE_TYPE_GIF; case "bmp" -> Document.PICTURE_TYPE_BMP; default -> Document.PICTURE_TYPE_PNG; };
        run.addPicture(new ByteArrayInputStream(image.data), type, "pdf-image." + image.format, Units.toEMU(width), Units.toEMU(height));
    }

    private static void clearCell(XWPFTableCell cell){while(cell.getParagraphs().size()>0)cell.removeParagraph(0);cell.addParagraph();}
    private static void setCellWidth(XWPFTableCell cell,double fraction){cell.setWidth(String.format("%.2f%%",Math.max(10,Math.min(90,fraction*100))));}
    private static void removeTableBorders(XWPFTable table){var borders=table.getCTTbl().getTblPr().isSetTblBorders()?table.getCTTbl().getTblPr().getTblBorders():table.getCTTbl().getTblPr().addNewTblBorders();borders.addNewTop().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);borders.addNewBottom().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);borders.addNewLeft().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);borders.addNewRight().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);borders.addNewInsideH().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);borders.addNewInsideV().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder.NONE);}
    private static int twips(double points){return(int)Math.max(0,Math.min(Integer.MAX_VALUE,Math.round(points*20)));}
    private static ResponseEntity<byte[]> error(String message){return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN).body(message.getBytes(StandardCharsets.UTF_8));}

    private static final class PositionStripper extends PDFTextStripper{
        private final List<Glyph> glyphs=new ArrayList<>(); PositionStripper()throws IOException{super();}
        @Override protected void writeString(String text,List<TextPosition> positions)throws IOException{for(TextPosition p:positions){String v=p.getUnicode();if(v==null||v.isEmpty())continue;String f=p.getFont()==null?"":p.getFont().getName();glyphs.add(new Glyph(v,p.getXDirAdj(),p.getYDirAdj(),p.getFontSizeInPt(),f));}}
        List<Line> lines(){glyphs.sort(Comparator.comparingDouble((Glyph g)->g.y).thenComparingDouble(g->g.x));List<Line> result=new ArrayList<>();for(Glyph g:glyphs){Line target=null;for(int i=result.size()-1;i>=0;i--){Line c=result.get(i);if(Math.abs(c.y-g.y)<=Math.max(2,g.size*.45)){target=c;break;}if(c.y<g.y-12)break;}if(target==null){target=new Line(g.y,g.x);result.add(target);}target.glyphs.add(g);target.x=Math.min(target.x,g.x);}result.sort(Comparator.comparingDouble(l->l.y));for(Line l:result)l.finish();for(int i=0;i+1<result.size();i++)result.get(i).afterGap=Math.max(0,result.get(i+1).y-result.get(i).y-result.maxSize);return result;}
    }

    private static final class ImageLocationExtractor extends PDFStreamEngine {
        private final PDPage page;
        private final List<EmbeddedImage> images = new ArrayList<>();
        ImageLocationExtractor(PDPage page) { this.page = page; }
        @Override protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if (OperatorName.DRAW_OBJECT.equals(operator.getName()) && !operands.isEmpty() && operands.get(0) instanceof COSName name) {
                PDXObject object = getResources().getXObject(name);
                if (object instanceof PDImageXObject image) {
                    Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
                    float width = Math.abs(ctm.getScalingFactorX());
                    float height = Math.abs(ctm.getScalingFactorY());
                    float x = ctm.getTranslateX();
                    float y = page.getMediaBox().getHeight() - ctm.getTranslateY() - height;
                    BufferedImage buffered = image.getImage();
                    if (buffered != null && buffered.getWidth() >= 8 && buffered.getHeight() >= 8) {
                        String format = image.getSuffix() == null ? "png" : image.getSuffix().toLowerCase();
                        if (!(format.equals("png") || format.equals("jpg") || format.equals("jpeg") || format.equals("gif") || format.equals("bmp"))) format = "png";
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        ImageIO.write(buffered, format, bytes);
                        images.add(new EmbeddedImage(bytes.toByteArray(), format, width, height, x, y));
                    }
                }
            }
            super.processOperator(operator, operands);
        }
        List<EmbeddedImage> images() { images.sort(Comparator.comparingDouble((EmbeddedImage i) -> i.y).thenComparingDouble(i -> i.x)); return images; }
    }

    private static final class Glyph{final String text,font;final double x,y,size;Glyph(String text,double x,double y,double size,String font){this.text=text;this.x=x;this.y=y;this.size=size;this.font=font;}}
    private static final class Line{final double y;double x,maxSize,afterGap;boolean heading;final List<Glyph> glyphs=new ArrayList<>();final List<Span> spans=new ArrayList<>();Line(double y,double x){this.y=y;this.x=x;}void finish(){glyphs.sort(Comparator.comparingDouble(g->g.x));Span current=null;for(Glyph g:glyphs){boolean bold=g.font.toLowerCase().contains("bold")||g.font.toLowerCase().contains("black");boolean italic=g.font.toLowerCase().contains("italic")||g.font.toLowerCase().contains("oblique");if(current==null||Math.abs(current.size-g.size)>.5||current.bold!=bold||current.italic!=italic||!current.font.equals(g.font)){current=new Span(g.text,g.size,bold,italic,g.font);spans.add(current);}else current.text+=g.text;maxSize=Math.max(maxSize,g.size);}double average=spans.stream().mapToDouble(s->s.size).average().orElse(10);int length=spans.stream().mapToInt(s->s.text.length()).sum();heading=average>=13||(length<=36&&spans.stream().anyMatch(s->s.bold));}}
    private static final class Span{String text;final double size;final boolean bold,italic;final String font;Span(String text,double size,boolean bold,boolean italic,String font){this.text=text;this.size=size;this.bold=bold;this.italic=italic;this.font=font;}}
    private record EmbeddedImage(byte[] data,String format,double width,double height,double x,double y){}
    private record PageModel(double width,double height,List<Line> lines,List<EmbeddedImage> images){}
}
