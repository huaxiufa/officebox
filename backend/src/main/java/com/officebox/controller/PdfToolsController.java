package com.officebox.controller;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

@RestController
@RequestMapping("/api/pdf")
public class PdfToolsController {

    @PostMapping(value = "/merge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> merge(@RequestParam("files") MultipartFile[] files) throws Exception {
        if (files == null || files.length < 2) {
            return ResponseEntity.badRequest().build();
        }

        int validFiles = 0;
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) validFiles++;
        }
        if (validFiles < 2) return ResponseEntity.badRequest().build();

        try (PDDocument destination = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;

                // Load and append the actual document. This avoids the previous
                // RandomAccessRead/InputStream lifecycle issue in PDFBox 3.
                try (PDDocument source = Loader.loadPDF(file.getBytes())) {
                    PDFMergerUtility merger = new PDFMergerUtility();
                    merger.appendDocument(destination, source);
                }
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
