package com.officebox.controller;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

@RestController
@RequestMapping("/api/pdf")
public class PdfAdvancedController {
    @PostMapping(value = "/split", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> split(@RequestParam("file") MultipartFile file, @RequestParam int page) throws Exception {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        try (PDDocument source = PDDocument.load(file.getBytes())) {
            if (page < 1 || page >= source.getNumberOfPages()) return ResponseEntity.badRequest().build();
            try (PDDocument first = new PDDocument(); PDDocument second = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                for (int i = 0; i < page; i++) first.importPage(source.getPage(i));
                for (int i = page; i < source.getNumberOfPages(); i++) second.importPage(source.getPage(i));
                java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out);
                java.io.ByteArrayOutputStream a = new java.io.ByteArrayOutputStream(); first.save(a);
                zip.putNextEntry(new java.util.zip.ZipEntry("part-1.pdf")); zip.write(a.toByteArray()); zip.closeEntry();
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream(); second.save(b);
                zip.putNextEntry(new java.util.zip.ZipEntry("part-2.pdf")); zip.write(b.toByteArray()); zip.closeEntry(); zip.finish(); zip.close();
                return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).header("Content-Disposition", "attachment; filename=\"officebox-split.zip\"").body(out.toByteArray());
            }
        }
    }

    @PostMapping(value = "/metadata", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> metadata(@RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) String title,
                                           @RequestParam(required = false) String author,
                                           @RequestParam(required = false) String subject,
                                           @RequestParam(required = false) String keywords) throws Exception {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        try (PDDocument doc = PDDocument.load(file.getBytes()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDDocumentInformation info = doc.getDocumentInformation();
            if (title != null) info.setTitle(title);
            if (author != null) info.setAuthor(author);
            if (subject != null) info.setSubject(subject);
            if (keywords != null) info.setKeywords(keywords);
            doc.save(out);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Content-Disposition", "attachment; filename=\"officebox-metadata.pdf\"").body(out.toByteArray());
        }
    }
}
