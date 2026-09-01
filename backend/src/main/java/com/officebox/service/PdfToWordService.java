package com.officebox.service;

import com.officebox.common.conversion.Pdf2DocxEngine;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * HTTP-facing PDF to Word service.
 *
 * The public API is intentionally kept stable, while the actual conversion is
 * delegated to the open-source pdf2docx engine. This class is the service used
 * by PdfToWordController, so the web endpoint cannot silently fall back to the
 * old Java renderer when the engine is installed in the production image.
 */
public class PdfToWordService {
    public static final String DOCX_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final Pdf2DocxEngine engine;

    public PdfToWordService() {
        this(new Pdf2DocxEngine());
    }

    PdfToWordService(Pdf2DocxEngine engine) {
        this.engine = engine;
    }

    public ResponseEntity<byte[]> convert(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String name = file.getOriginalFilename() == null
                ? "document.pdf"
                : file.getOriginalFilename();
        if (!name.toLowerCase().endsWith(".pdf")) {
            return error(HttpStatusCode.BAD_REQUEST, "请选择 PDF 文件");
        }

        Path input = null;
        Path output = null;
        try {
            input = Files.createTempFile("officebox-pdf-word-", ".pdf");
            output = Files.createTempFile("officebox-pdf-word-", ".docx");
            Files.write(input, file.getBytes());
            Files.deleteIfExists(output);

            // This is the actual production conversion path. The engine runs
            // Artifex pdf2docx inside the Docker image and reconstructs editable
            // DOCX layout instead of embedding a screenshot of the PDF page.
            if (!engine.isAvailable()) {
                return error(HttpStatusCode.SERVER_ERROR,
                        "PDF 转 Word 引擎未安装，请使用包含 pdf2docx 的 OfficeBox Docker 镜像");
            }
            if (!engine.convert(input, output)) {
                return error(HttpStatusCode.SERVER_ERROR, "PDF 转 Word 未生成 DOCX 文件");
            }

            byte[] body = Files.readAllBytes(output);
            if (body.length == 0) {
                return error(HttpStatusCode.SERVER_ERROR, "PDF 转 Word 生成了空文件");
            }

            String outputName = name.substring(0, name.length() - 4) + ".docx";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(DOCX_TYPE))
                    .header("Content-Disposition", "attachment; filename=\"" + safeHeader(outputName) + "\"")
                    .body(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(HttpStatusCode.SERVER_ERROR, "PDF 转 Word 被中断");
        } catch (Exception e) {
            return error(HttpStatusCode.SERVER_ERROR,
                    "PDF 转 Word 失败: " + (e.getMessage() == null
                            ? e.getClass().getSimpleName()
                            : e.getMessage()));
        } finally {
            delete(input);
            delete(output);
        }
    }

    private static String safeHeader(String value) {
        return value.replace("\\", "_").replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }

    private static void delete(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Best effort cleanup of temporary conversion files.
            }
        }
    }

    private static ResponseEntity<byte[]> error(HttpStatusCode status, String message) {
        return ResponseEntity.status(status.code)
                .contentType(MediaType.TEXT_PLAIN)
                .body(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private enum HttpStatusCode {
        BAD_REQUEST(400),
        SERVER_ERROR(500);

        final int code;
        HttpStatusCode(int code) {
            this.code = code;
        }
    }
}
