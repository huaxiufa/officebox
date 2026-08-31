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
import java.util.ArrayList;
import java.util.List;
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
            Path input = dir.resolve("document-" + UUID.randomUUID() + ".pdf");
            Files.write(input, file.getBytes());

            // First try Writer's native PDF import. This preserves real PDF text much better than
            // treating every page as a Draw canvas. If the PDF contains scanned/image text, fall back
            // to OCR so the generated DOCX contains selectable text instead of only a page image.
            Path nativeDocx = dir.resolve("native.docx");
            ProcessResult nativeResult = run(dir.resolve("lo-profile"), List.of(
                    "libreoffice", "--headless", "--nologo", "--nodefault", "--nofirststartwizard",
                    "-env:UserInstallation=file:" + Files.createDirectories(dir.resolve("lo-profile")).toAbsolutePath(),
                    "--infilter=writer_pdf_import", "--convert-to", "docx:Office Open XML Text",
                    "--outdir", dir.toString(), input.toString()));
            Path generated = dir.resolve(stripExtension(input.getFileName().toString()) + ".docx");
            if (nativeResult.exitCode == 0 && Files.exists(generated) && Files.size(generated) > 0) {
                byte[] bytes = Files.readAllBytes(generated);
                return docx(bytes);
            }

            // Image/scanned PDF fallback: render pages and OCR Chinese + English.
            Path pages = Files.createDirectories(dir.resolve("pages"));
            ProcessResult render = run(dir.resolve("render-profile"), List.of(
                    "pdftoppm", "-r", "180", "-png", input.toString(), pages.resolve("page").toString()));
            if (render.exitCode != 0) throw new IllegalStateException("PDF 页面渲染失败: " + render.output);

            List<Path> pngs;
            try (var s = Files.list(pages)) {
                pngs = s.filter(p -> p.getFileName().toString().endsWith(".png")).sorted().toList();
            }
            if (pngs.isEmpty()) throw new IllegalStateException("PDF 没有可处理的页面");

            StringBuilder html = new StringBuilder("<!doctype html><html><head><meta charset='utf-8'><style>body{font-family:'Noto Sans CJK SC',Arial,sans-serif;font-size:12pt}h2{page-break-before:always}pre{white-space:pre-wrap;word-wrap:break-word;line-height:1.5}</style></head><body>");
            int pageNo = 0;
            for (Path png : pngs) {
                pageNo++;
                Path txt = pages.resolve("ocr-" + pageNo);
                ProcessResult ocr = run(dir.resolve("ocr-profile-" + pageNo), List.of(
                        "tesseract", png.toString(), txt.toString(), "-l", "chi_sim+eng", "--psm", "3"));
                if (ocr.exitCode != 0) throw new IllegalStateException("OCR 第 " + pageNo + " 页失败: " + ocr.output);
                Path txtFile = Path.of(txt + ".txt");
                String text = Files.exists(txtFile) ? Files.readString(txtFile, StandardCharsets.UTF_8) : "";
                html.append(pageNo == 1 ? "" : "<h2>第 ").append(pageNo == 1 ? "" : pageNo + " 页</h2>");
                html.append("<pre>").append(escapeHtml(text)).append("</pre>");
            }
            html.append("</body></html>");
            Path htmlFile = dir.resolve("ocr-result.html");
            Files.writeString(htmlFile, html.toString(), StandardCharsets.UTF_8);

            Path out = Files.createDirectories(dir.resolve("docx-out"));
            ProcessResult convert = run(dir.resolve("docx-profile"), List.of(
                    "libreoffice", "--headless", "--nologo", "--nodefault", "--nofirststartwizard",
                    "-env:UserInstallation=file:" + Files.createDirectories(dir.resolve("docx-profile")).toAbsolutePath(),
                    "--convert-to", "docx:Office Open XML Text", "--outdir", out.toString(), htmlFile.toString()));
            Path docx = out.resolve("ocr-result.docx");
            if (convert.exitCode != 0 || !Files.exists(docx) || Files.size(docx) == 0) {
                throw new IllegalStateException("OCR 后未生成 DOCX 文件: " + convert.output);
            }
            return docx(Files.readAllBytes(docx));
        } catch (Exception e) {
            return error("PDF 转 Word 失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            if (dir != null) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                } catch (IOException ignored) {}
            }
        }
    }

    private static ProcessResult run(Path profile, List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (!p.waitFor(120, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IllegalStateException("处理超时"); }
        return new ProcessResult(p.exitValue(), output);
    }

    private record ProcessResult(int exitCode, String output) {}

    private static ResponseEntity<byte[]> docx(byte[] bytes) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(DOCX_TYPE))
                .header("Content-Disposition", "attachment; filename=\"document.docx\"").body(bytes);
    }

    private static ResponseEntity<byte[]> error(String message) {
        return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN)
                .body(message.getBytes(StandardCharsets.UTF_8));
    }

    private static String stripExtension(String n) { int i = n.lastIndexOf('.'); return i < 0 ? n : n.substring(0, i); }
    private static String escapeHtml(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
