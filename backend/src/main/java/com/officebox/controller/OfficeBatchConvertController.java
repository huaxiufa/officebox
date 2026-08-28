package com.officebox.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/office")
public class OfficeBatchConvertController {
    private static final Set<String> SUPPORTED = Set.of("doc", "docx", "xls", "xlsx", "ppt", "pptx");
    private static final int MAX_FILES = 20;

    @PostMapping(value = "/batch-to-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> batchToPdf(@RequestParam("files") MultipartFile[] files) throws Exception {
        if (files == null || files.length == 0 || files.length > MAX_FILES) return ResponseEntity.badRequest().build();
        Path dir = Files.createTempDirectory("officebox-batch-");
        Path inputDir = Files.createDirectories(dir.resolve("input"));
        Path outputDir = Files.createDirectories(dir.resolve("output"));
        List<Path> outputs = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;
                String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
                String ext = extension(original);
                if (!SUPPORTED.contains(ext)) continue;
                String base = UUID.randomUUID().toString();
                Path input = inputDir.resolve(base + "." + ext);
                Files.write(input, file.getBytes());
                Process p = new ProcessBuilder("libreoffice", "--headless", "--convert-to", "pdf", "--outdir", outputDir.toString(), input.toString()).redirectErrorStream(true).start();
                if (!p.waitFor(120, TimeUnit.SECONDS)) { p.destroyForcibly(); continue; }
                if (p.exitValue() != 0) continue;
                Path generated = outputDir.resolve(base + ".pdf");
                if (Files.exists(generated)) {
                    Path named = outputDir.resolve(uniqueSafePdfName(outputDir, original));
                    Files.move(generated, named, StandardCopyOption.REPLACE_EXISTING);
                    outputs.add(named);
                }
            }
            if (outputs.isEmpty()) return ResponseEntity.unprocessableEntity().build();
            Path zip = dir.resolve("officebox-pdf.zip");
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
                for (Path pdf : outputs) { zos.putNextEntry(new ZipEntry(pdf.getFileName().toString())); Files.copy(pdf, zos); zos.closeEntry(); }
            }
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                    .header("Content-Disposition", "attachment; filename=\"officebox-pdf.zip\"").body(Files.readAllBytes(zip));
        } finally {
            try (var stream = Files.walk(dir)) { stream.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} }); }
        }
    }

    private static String extension(String name) { int i = name.lastIndexOf('.'); return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT); }
    private static String uniqueSafePdfName(Path dir, String original) {
        String base = original.replaceFirst("\\.[^.]+$", "").replaceAll("[\\\\/:*?\"<>|]", "_");
        if (base.isBlank()) base = "document";
        Path candidate = dir.resolve(base + ".pdf"); int i = 2;
        while (Files.exists(candidate)) candidate = dir.resolve(base + "-" + i++ + ".pdf");
        return candidate.getFileName().toString();
    }
}
