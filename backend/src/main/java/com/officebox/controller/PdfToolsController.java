package com.officebox.controller;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/pdf")
public class PdfToolsController {

    @PostMapping(value = "/merge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> merge(@RequestParam("files") MultipartFile[] files) throws Exception {
        if (files == null || files.length < 2) return ResponseEntity.badRequest().build();
        PDFMergerUtility merger = new PDFMergerUtility();
        List<MultipartFile> valid = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f != null && !f.isEmpty()) { PDDocument.load(f.getBytes()).close(); valid.add(f); }
        }
        if (valid.size() < 2) return ResponseEntity.badRequest().build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        merger.setDestinationStream(out);
        for (MultipartFile f : valid) merger.addSource(f.getInputStream());
        merger.mergeDocuments(null);
        return pdf(out.toByteArray(), "officebox-merged.pdf");
    }

    @PostMapping(value = "/rotate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> rotate(@RequestParam("file") MultipartFile file,
                                         @RequestParam(defaultValue = "90") int degrees) throws Exception {
        if (file == null || file.isEmpty() || degrees % 90 != 0) return ResponseEntity.badRequest().build();
        int normalized = ((degrees % 360) + 360) % 360;
        try (PDDocument doc = PDDocument.load(file.getBytes()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (PDPage page : doc.getPages()) page.setRotation((page.getRotation() + normalized) % 360);
            doc.save(out);
            return pdf(out.toByteArray(), "officebox-rotated.pdf");
        }
    }

    private ResponseEntity<byte[]> pdf(byte[] bytes, String name) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"").body(bytes);
    }
}
