package com.officebox.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/pdf")
public class PdfToWordController {
    private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @PostMapping(value = "/to-word", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> toWord(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        String original = file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename();
        if (!original.toLowerCase().endsWith(".pdf")) return ResponseEntity.badRequest().build();

        Path dir = null;
        try {
            dir = Files.createTempDirectory("officebox-pdf-word-");
            Path profile = Files.createDirectory(dir.resolve("lo-profile"));
            Path input = dir.resolve("document-" + UUID.randomUUID() + ".pdf");
            Files.write(input, file.getBytes());

            Process p = new ProcessBuilder(
                    "libreoffice", "--headless", "--nologo", "--nodefault", "--nofirststartwizard",
                    "-env:UserInstallation=file:" + profile.toAbsolutePath(),
                    "--infilter=writer_pdf_import",
                    "--convert-to", "docx:Office Open XML Text",
                    "--outdir", dir.toString(), input.toString())
                    .redirectErrorStream(true).start();

            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            boolean finished = p.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return error("LibreOffice 转换超时");
            }

            Path docx = dir.resolve(stripExtension(input.getFileName().toString()) + ".docx");
            if (p.exitValue() != 0 || !Files.exists(docx) || Files.size(docx) == 0) {
                return error("LibreOffice 转换失败" + (output.isEmpty() ? "" : ": " + output));
            }

            byte[] bytes = Files.readAllBytes(docx);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(DOCX_TYPE))
                    .header("Content-Disposition", "attachment; filename=\"document.docx\"")
                    .body(bytes);
        } catch (Exception e) {
            return error("PDF 转 Word 失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            if (dir != null) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                    });
                } catch (IOException ignored) { }
            }
        }
    }

    private static ResponseEntity<byte[]> error(String message) {
        return ResponseEntity.internalServerError()
                .contentType(MediaType.TEXT_PLAIN)
                .body(message.getBytes(StandardCharsets.UTF_8));
    }

    private static String stripExtension(String n) {
        int i = n.lastIndexOf('.');
        return i < 0 ? n : n.substring(0, i);
    }
}
