package com.officebox.service;

import com.officebox.common.conversion.DoclingEngine;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** HTTP-facing PDF to Word service backed by Docling. */
public class PdfToWordService {
    public static final String DOCX_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DoclingEngine engine;

    public PdfToWordService() {
        this(new DoclingEngine());
    }

    PdfToWordService(DoclingEngine engine) {
        this.engine = engine;
    }

    public ResponseEntity<byte[]> convert(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String name = file.getOriginalFilename() == null
                ? "document.pdf"
                : file.getOriginalFilename();
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            return error(400, "请选择 PDF 文件");
        }

        Path input = null;
        Path output = null;
        try {
            input = Files.createTempFile("officebox-pdf-word-", ".pdf");
            output = Files.createTempFile("officebox-pdf-word-", ".docx");
            Files.write(input, file.getBytes());
            Files.deleteIfExists(output);

            // Production path: PDF -> Docling -> structured HTML -> LibreOffice -> DOCX.
            // There is deliberately no pdf2docx fallback here, so this endpoint
            // cannot silently return a document produced by the old engine.
            if (!engine.isAvailable()) {
                return error(500,
                        "PDF 转 Word 引擎未安装，请使用包含 Docling 和 LibreOffice 的 OfficeBox Docker 镜像");
            }
            if (!engine.convert(input, output)) {
                return error(500, "PDF 转 Word 未生成 DOCX 文件");
            }

            byte[] body = Files.readAllBytes(output);
            if (body.length == 0) {
                return error(500, "PDF 转 Word 生成了空文件");
            }

            String outputName = name.substring(0, name.length() - 4) + ".docx";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(DOCX_TYPE))
                    .header("Content-Disposition", "attachment; filename=\"" + safeHeader(outputName) + "\"")
                    .body(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(500, "PDF 转 Word 被中断");
        } catch (Exception e) {
            return error(500,
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
                // Best-effort cleanup.
            }
        }
    }

    private static ResponseEntity<byte[]> error(int status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_PLAIN)
                .body(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
