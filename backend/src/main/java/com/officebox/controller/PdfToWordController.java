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
    public ResponseEntity<byte[]> toWord(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        String original = file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename();
        if (!original.toLowerCase().endsWith(".pdf")) return ResponseEntity.badRequest().build();

        Path dir = Files.createTempDirectory("officebox-pdf-word-");
        Path input = dir.resolve("document-" + UUID.randomUUID() + ".pdf");
        Path profile = dir.resolve("lo-profile");
        Files.write(input, file.getBytes());
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    "libreoffice",
                    "--headless",
                    "--nologo",
                    "--nodefault",
                    "--nofirststartwizard",
                    "-env:UserInstallation=" + profile.toUri(),
                    "--convert-to", "docx:Office Open XML Text",
                    "--outdir", dir.toString(),
                    input.toString());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            byte[] output = process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("LibreOffice 转换超时");
            }
            if (process.exitValue() != 0) {
                String message = new String(output, StandardCharsets.UTF_8).trim();
                throw new IllegalStateException("LibreOffice 转换失败" + (message.isEmpty() ? "" : ": " + message));
            }

            Path result = dir.resolve(stripExtension(input.getFileName().toString()) + ".docx");
            if (!Files.exists(result)) {
                throw new IllegalStateException("LibreOffice 未生成 DOCX 文件");
            }
            byte[] bytes = Files.readAllBytes(result);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(DOCX_TYPE))
                    .header("Content-Disposition", "attachment; filename=\"document.docx\"")
                    .body(bytes);
        } catch (IllegalStateException e) {
            return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN).body(e.getMessage().getBytes(StandardCharsets.UTF_8));
        } finally {
            try (var stream = Files.walk(dir)) {
                stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                });
            }
        }
    }

    private static String stripExtension(String n) {
        int i = n.lastIndexOf('.');
        return i < 0 ? n : n.substring(0, i);
    }
}
