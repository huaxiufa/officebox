package com.officebox.controller;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

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
        if (file == null || file.isEmpty() || password == null) return ResponseEntity.badRequest().build();
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(file.getBytes()), password); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!doc.isEncrypted()) return pdf(file.getBytes(), "officebox-decrypted.pdf");
            doc.setAllSecurityToBeRemoved(true);
            doc.save(out);
            return pdf(out.toByteArray(), "officebox-decrypted.pdf");
        }
    }

    @PostMapping(value = "/compress", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> compress(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
        try (PDDocument doc = Loader.loadPDF(file.getBytes()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.getDocumentCatalog().getCOSObject().removeItem(COSName.METADATA);
            doc.getDocumentInformation().setAuthor(null);
            doc.getDocumentInformation().setCreator(null);
            doc.getDocumentInformation().setProducer(null);
            doc.save(out, CompressParameters.DEFAULT_COMPRESSION);
            return pdf(out.toByteArray(), "officebox-compressed.pdf");
        }
    }

    private ResponseEntity<byte[]> pdf(byte[] bytes, String name) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"").body(bytes);
    }
}
