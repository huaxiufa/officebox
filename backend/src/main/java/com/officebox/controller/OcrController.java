package com.officebox.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> image(@RequestParam("file") MultipartFile file,
                                        @RequestParam(defaultValue = "chi_sim+eng") String lang) throws Exception {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body("请选择图片");
        String safeLang = lang.matches("[a-zA-Z_+]+") ? lang : "chi_sim+eng";
        Path dir = Files.createTempDirectory("officebox-ocr-");
        Path input = dir.resolve("input-" + UUID.randomUUID() + extension(file.getOriginalFilename()));
        Path output = dir.resolve("result");
        try {
            Files.write(input, file.getBytes());
            Process p = new ProcessBuilder("tesseract", input.toString(), output.toString(), "-l", safeLang)
                    .redirectErrorStream(true).start();
            String logs = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(120, TimeUnit.SECONDS)) { p.destroyForcibly(); return ResponseEntity.status(504).body("OCR 超时"); }
            if (p.exitValue() != 0) return ResponseEntity.unprocessableEntity().body("OCR 失败: " + logs.trim());
            Path txt = Path.of(output + ".txt");
            if (!Files.exists(txt)) return ResponseEntity.unprocessableEntity().body("OCR 未生成文本");
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Files.readString(txt, StandardCharsets.UTF_8));
        } finally {
            try (var s = Files.walk(dir)) { s.sorted(java.util.Comparator.reverseOrder()).forEach(x -> { try { Files.deleteIfExists(x); } catch (IOException ignored) {} }); }
        }
    }
    private String extension(String name) { if (name == null) return ".bin"; int i=name.lastIndexOf('.'); return i>0 && i<name.length()-1 ? name.substring(i).toLowerCase(Locale.ROOT) : ".bin"; }
}
