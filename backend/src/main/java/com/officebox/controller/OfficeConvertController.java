package com.officebox.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/office")
public class OfficeConvertController {
    private static final Set<String> SUPPORTED = Set.of("doc", "docx", "xls", "xlsx", "ppt", "pptx");

    @PostMapping(value = "/to-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> toPdf(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        String name = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String ext = extension(name);
        if (!SUPPORTED.contains(ext)) return ResponseEntity.badRequest().build();

        Path dir = Files.createTempDirectory("officebox-");
        Path input = dir.resolve(UUID.randomUUID() + "." + ext);
        Files.write(input, file.getBytes());
        try {
            Process p = new ProcessBuilder("libreoffice", "--headless", "--convert-to", "pdf", "--outdir", dir.toString(), input.toString())
                    .redirectErrorStream(true).start();
            if (!p.waitFor(120, TimeUnit.SECONDS) || p.exitValue() != 0) {
                p.destroyForcibly();
                return ResponseEntity.internalServerError().build();
            }
            Path output = dir.resolve(stripExtension(input.getFileName().toString()) + ".pdf");
            if (!Files.exists(output)) return ResponseEntity.internalServerError().build();
            byte[] bytes = Files.readAllBytes(output);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header("Content-Disposition", "attachment; filename=\"" + safeName(stripExtension(name)) + ".pdf\"")
                    .body(bytes);
        } finally {
            try (var s = Files.walk(dir)) { s.sorted((a,b) -> b.compareTo(a)).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} }); }
        }
    }

    private static String extension(String n) { int i=n.lastIndexOf('.'); return i<0 ? "" : n.substring(i+1).toLowerCase(Locale.ROOT); }
    private static String stripExtension(String n) { int i=n.lastIndexOf('.'); return i<0 ? n : n.substring(0,i); }
    private static String safeName(String n) { return n.replaceAll("[\\\\/:*?\"<>|]", "_"); }
}
