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
        Path input = dir.resolve(UUID.randomUUID() + ".pdf");
        Files.write(input, file.getBytes());
        try {
            Process p = new ProcessBuilder("libreoffice", "--headless", "--convert-to", "docx:Office Open XML Text", "--outdir", dir.toString(), input.toString())
                    .redirectErrorStream(true).start();
            if (!p.waitFor(120, TimeUnit.SECONDS) || p.exitValue() != 0) {
                p.destroyForcibly();
                return ResponseEntity.internalServerError().build();
            }
            Path output = dir.resolve(stripExtension(input.getFileName().toString()) + ".docx");
            if (!Files.exists(output)) return ResponseEntity.internalServerError().build();
            byte[] bytes = Files.readAllBytes(output);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
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
