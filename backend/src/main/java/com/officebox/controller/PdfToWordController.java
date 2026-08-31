package com.officebox.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/pdf")
public class PdfToWordController {
    @PostMapping(value = "/to-word", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> toWord(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        String original = file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename();
        if (!original.toLowerCase().endsWith(".pdf")) return ResponseEntity.badRequest().build();

        Path dir = Files.createTempDirectory("officebox-pdf-word-");
        Path profile = Files.createDirectory(dir.resolve("lo-profile"));
        Path input = dir.resolve(UUID.randomUUID() + ".pdf");
        Files.write(input, file.getBytes());
        try {
            Process p = new ProcessBuilder(
                    "libreoffice", "--headless", "--nologo", "--nodefault", "--nofirststartwizard",
                    "-env:UserInstallation=file:" + profile.toAbsolutePath(),
                    "--convert-to", "docx:Office Open XML Text", "--outdir", dir.toString(), input.toString())
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes());
            boolean finished = p.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new IllegalStateException("LibreOffice 转换超时");
            }
            Path docx = dir.resolve(stripExtension(input.getFileName().toString()) + ".docx");
            if (p.exitValue() != 0 || !Files.exists(docx) || Files.size(docx) == 0) {
                String detail = output == null ? "" : output.trim();
                throw new IllegalStateException("LibreOffice 未生成 DOCX 文件" + (detail.isEmpty() ? "" : ": " + detail));
            }
            byte[] bytes = Files.readAllBytes(docx);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .header("Content-Disposition", "attachment; filename=\"document.docx\"")
                    .body(bytes);
        } finally {
            try (var stream = Files.walk(dir)) {
                stream.sorted((a,b) -> b.compareTo(a)).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        }
    }

    private static String stripExtension(String n) {
        int i = n.lastIndexOf('.');
        return i < 0 ? n : n.substring(0, i);
    }
}
