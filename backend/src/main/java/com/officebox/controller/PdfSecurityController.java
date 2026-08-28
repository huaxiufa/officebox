package com.officebox.controller;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/pdf")
public class PdfSecurityController {
    @PostMapping(value = "/encrypt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> encrypt(@RequestParam("file") MultipartFile file,
                                          @RequestParam("password") String password) throws Exception {
        if (file == null || file.isEmpty() || password == null || password.isBlank()) return ResponseEntity.badRequest().build();
        try (PDDocument doc = Loader.loadPDF(file.getBytes()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            AccessPermission permission = new AccessPermission();
            permission.setCanPrint(true);
            StandardProtectionPolicy policy = new StandardProtectionPolicy(password, password, permission);
            policy.setEncryptionKeyLength(256);
            doc.protect(policy);
            doc.save(out);
            return pdf(out.toByteArray(), "officebox-encrypted.pdf");
        }
    }

    @PostMapping(value = "/decrypt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> decrypt(@RequestParam("file") MultipartFile file,
                                          @RequestParam("password") String password) throws Exception {
        if (file == null || file.isEmpty() || password == null || password.isBlank()) return ResponseEntity.badRequest().build();
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(file.getBytes()), password); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!doc.isEncrypted()) return pdf(file.getBytes(), "officebox-decrypted.pdf");
            doc.setAllSecurityToBeRemoved(true);
            doc.save(out);
            return pdf(out.toByteArray(), "officebox-decrypted.pdf");
        } catch (InvalidPasswordException e) {
            return ResponseEntity.status(400).contentType(MediaType.TEXT_PLAIN).body("PDF 密码错误，请输入正确的打开密码。".getBytes());
        }
    }

    @PostMapping(value = "/compress", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> compress(@RequestParam("file") MultipartFile file,
                                           @RequestParam(value = "level", defaultValue = "ebook") String level) throws Exception {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        String setting = switch (level) {
            case "screen" -> "screen";
            case "printer" -> "printer";
            default -> "ebook";
        };
        Path input = Files.createTempFile("officebox-in-", ".pdf");
        Path output = Files.createTempFile("officebox-out-", ".pdf");
        try {
            Files.write(input, file.getBytes());
            Process process = new ProcessBuilder(List.of(
                    "gs", "-sDEVICE=pdfwrite", "-dCompatibilityLevel=1.4", "-dPDFSETTINGS=/" + setting,
                    "-dNOPAUSE", "-dQUIET", "-dBATCH", "-sOutputFile=" + output, input.toString()
            )).redirectErrorStream(true).start();
            String log = new String(process.getInputStream().readAllBytes());
            int exit = process.waitFor();
            if (exit != 0 || !Files.exists(output) || Files.size(output) == 0) {
                throw new IOException("PDF 压缩失败: " + log);
            }
            byte[] compressed = Files.readAllBytes(output);
            if (compressed.length >= file.getSize()) {
                return pdf(file.getBytes(), "officebox-compressed.pdf");
            }
            return pdf(compressed, "officebox-compressed.pdf");
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }
    }

    private ResponseEntity<byte[]> pdf(byte[] bytes, String name) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"").body(bytes);
    }
}
