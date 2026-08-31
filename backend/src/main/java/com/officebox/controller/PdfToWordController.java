package com.officebox.controller;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
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

            try (PDDocument pdf = Loader.loadPDF(input.toFile())) {
                if (pdf.getNumberOfPages() == 0) return error("PDF 没有页面");

                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                stripper.setShouldSeparateByBeads(true);

                try (XWPFDocument docx = new XWPFDocument()) {
                    boolean hasText = false;
                    for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                        stripper.setStartPage(page);
                        stripper.setEndPage(page);
                        String text = stripper.getText(pdf);
                        if (text != null && !text.isBlank()) hasText = true;

                        // Build an editable Word document from the PDF text layer instead of
                        // asking LibreOffice to convert the PDF as a Draw canvas.
                        String[] lines = text == null ? new String[0] : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
                        for (String line : lines) {
                            XWPFParagraph paragraph = docx.createParagraph();
                            paragraph.setSpacingAfter(0);
                            paragraph.createRun().setText(line);
                        }
                        if (page < pdf.getNumberOfPages()) {
                            XWPFParagraph break = docx.createParagraph();
                            break.createRun().addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE);
                        }
                    }

                    // A PDF with no text layer needs OCR; don't silently return an empty DOCX.
                    if (!hasText) return error("PDF 没有可提取的文字层，请使用可搜索文字的 PDF；扫描版 PDF 将在下一版启用 OCR 转换");

                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    docx.write(out);
                    return ResponseEntity.ok().contentType(MediaType.parseMediaType(DOCX_TYPE))
                            .header("Content-Disposition", "attachment; filename=\"document.docx\"")
                            .body(out.toByteArray());
                }
            }
        } catch (Exception e) {
            return error("PDF 转 Word 失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            if (input != null) try { Files.deleteIfExists(input); } catch (Exception ignored) { }
        }
    }

    private static ResponseEntity<byte[]> error(String message) {
        return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN)
                .body(message.getBytes(StandardCharsets.UTF_8));
    }
}
