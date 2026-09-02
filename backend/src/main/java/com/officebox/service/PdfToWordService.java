package com.officebox.service;

import com.officebox.common.conversion.DoclingEngine;
import com.officebox.common.conversion.Pdf2DocxEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** HTTP-facing PDF to Word service. Docling is primary; pdf2docx is a safety fallback. */
public class PdfToWordService {
    public static final String DOCX_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final Logger log = LoggerFactory.getLogger(PdfToWordService.class);

    private final DoclingEngine docling;
    private final Pdf2DocxEngine pdf2docx;

    public PdfToWordService() {
        this(new DoclingEngine(), new Pdf2DocxEngine());
    }

    PdfToWordService(DoclingEngine docling, Pdf2DocxEngine pdf2docx) {
        this.docling = docling;
        this.pdf2docx = pdf2docx;
    }

    public ResponseEntity<byte[]> convert(MultipartFile file) {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        String name = file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename();
        if (!name.toLowerCase().endsWith(".pdf")) return error(400, "请选择 PDF 文件");

        Path input = null;
        Path output = null;
        Path fallbackOutput = null;
        try {
            input = Files.createTempFile("officebox-pdf-word-", ".pdf");
            output = Files.createTempFile("officebox-pdf-word-", ".docx");
            fallbackOutput = Files.createTempFile("officebox-pdf-word-fallback-", ".docx");
            Files.write(input, file.getBytes());
            Files.deleteIfExists(output);
            Files.deleteIfExists(fallbackOutput);

            Exception doclingFailure = null;
            if (docling.isAvailable()) {
                try {
                    if (docling.convert(input, output)) {
                        return success(output, name);
                    }
                } catch (Exception e) {
                    doclingFailure = e;
                    log.warn("Docling PDF to Word conversion failed; trying pdf2docx fallback", e);
                }
            } else {
                doclingFailure = new IOException("Docling engine is not available");
                log.warn("Docling engine is not available; trying pdf2docx fallback");
            }

            if (pdf2docx.isAvailable()) {
                try {
                    if (pdf2docx.convert(input, fallbackOutput)) {
                        log.warn("PDF to Word request completed with pdf2docx fallback");
                        return success(fallbackOutput, name);
                    }
                } catch (Exception fallbackFailure) {
                    log.error("Both Docling and pdf2docx PDF to Word conversion failed", fallbackFailure);
                    return error(500, "PDF 转 Word 失败: Docling=" + summarize(doclingFailure)
                            + "; pdf2docx=" + summarize(fallbackFailure));
                }
            }

            return error(500, "PDF 转 Word 失败: Docling=" + summarize(doclingFailure)
                    + "; pdf2docx 引擎不可用");
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return error(500, "PDF 转 Word 被中断");
            }
            log.error("PDF to Word request failed before conversion completed", e);
            return error(500, "PDF 转 Word 失败: " + summarize(e));
        } finally {
            delete(input);
            delete(output);
            delete(fallbackOutput);
        }
    }

    private static ResponseEntity<byte[]> success(Path output, String inputName) throws IOException {
        byte[] body = Files.readAllBytes(output);
        if (body.length == 0) throw new IOException("生成了空 DOCX 文件");
        String outputName = inputName.substring(0, inputName.length() - 4) + ".docx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DOCX_TYPE))
                .header("Content-Disposition", "attachment; filename=\"" + safeHeader(outputName) + "\"")
                .body(body);
    }

    private static String summarize(Exception e) {
        if (e == null) return "不可用";
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message.length() > 1200 ? message.substring(0, 1200) + "..." : message;
    }

    private static String safeHeader(String value) {
        return value.replace("\\", "_").replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }

    private static void delete(Path path) {
        if (path != null) try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    private static ResponseEntity<byte[]> error(int code, String message) {
        return ResponseEntity.status(code).contentType(MediaType.TEXT_PLAIN)
                .body(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
