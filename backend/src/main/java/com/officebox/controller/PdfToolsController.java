package com.officebox.controller;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

@RestController
@RequestMapping("/api/pdf")
public class PdfToolsController {

    @PostMapping(value = "/merge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> merge(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "password", required = false, defaultValue = "") String password) throws Exception {
        if (files == null || files.length < 2) {
            return ResponseEntity.badRequest().body("合并至少需要 2 个 PDF 文件".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        int validFiles = 0;
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) validFiles++;
        }
        if (validFiles < 2) {
            return ResponseEntity.badRequest().body("合并至少需要 2 个有效 PDF 文件".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        try (PDDocument destination = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;

                try (PDDocument source = password.isBlank()
                        ? Loader.loadPDF(file.getBytes())
                        : Loader.loadPDF(file.getBytes(), password)) {
                    PDFMergerUtility merger = new PDFMergerUtility();
                    merger.appendDocument(destination, source);
                } catch (InvalidPasswordException e) {
                    String message = password.isBlank()
                            ? "检测到加密 PDF，请填写正确的合并密码"
                            : "PDF 密码错误，请确认所有加密 PDF 使用的密码正确";
                    return ResponseEntity.badRequest()
                            .contentType(MediaType.TEXT_PLAIN)
                            .body(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }

            if (destination.getNumberOfPages() == 0) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("没有可合并的 PDF 页面".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            destination.save(out);
            return pdf(out.toByteArray(), "officebox-merged.pdf");
        }
    }

    @PostMapping(value = "/rotate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> rotate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "90") int degrees) throws Exception {
        if (file == null || file.isEmpty() || degrees % 90 != 0) {
            return ResponseEntity.badRequest().build();
        }
        int n = ((degrees % 360) + 360) % 360;
        try (PDDocument doc = Loader.loadPDF(file.getBytes());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (PDPage page : doc.getPages()) {
                page.setRotation((page.getRotation() + n) % 360);
            }
            doc.save(out);
            return pdf(out.toByteArray(), "officebox-rotated.pdf");
        }
    }

    private ResponseEntity<byte[]> pdf(byte[] bytes, String name) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                .body(bytes);
    }
}
