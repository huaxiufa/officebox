package com.officebox.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@RestController
@RequestMapping("/api/pdf")
@CrossOrigin(origins = "*")
public class PdfSecurityController {

  @PostMapping(value = "/watermark", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
  public byte[] watermark(@RequestPart("file") MultipartFile file,
                          @RequestParam(defaultValue = "OfficeBox") String text,
                          @RequestParam(defaultValue = "0.22") float opacity) throws IOException {
    if (text.isBlank()) throw new IllegalArgumentException("水印文字不能为空");
    opacity = Math.max(0.05f, Math.min(opacity, 1f));
    try (PDDocument doc = Loader.loadPDF(file.getBytes()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      for (PDPage page : doc.getPages()) {
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
          float width = page.getMediaBox().getWidth(), height = page.getMediaBox().getHeight();
          cs.saveGraphicsState();
          cs.setNonStrokingColor(0.45f, 0.45f, 0.55f);
          cs.beginText();
          cs.setFont(font, 34);
          cs.setTextRotation(Math.toRadians(35), width / 2f, height / 2f);
          cs.showText(text);
          cs.endText();
          cs.restoreGraphicsState();
        }
      }
      doc.save(out);
      return out.toByteArray();
    }
  }

  @PostMapping(value = "/encrypt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
  public byte[] encrypt(@RequestPart("file") MultipartFile file, @RequestParam String password) throws IOException {
    if (password == null || password.length() < 4) throw new IllegalArgumentException("密码至少需要 4 位");
    try (PDDocument doc = Loader.loadPDF(file.getBytes()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      AccessPermission permission = new AccessPermission();
      StandardProtectionPolicy policy = new StandardProtectionPolicy(password, password, permission);
      policy.setEncryptionKeyLength(256);
      policy.setPermissions(permission);
      doc.protect(policy);
      doc.save(out);
      return out.toByteArray();
    }
  }

  @PostMapping(value = "/decrypt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
  public byte[] decrypt(@RequestPart("file") MultipartFile file, @RequestParam String password) throws IOException {
    try (PDDocument doc = Loader.loadPDF(file.getBytes(), password); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      if (doc.isEncrypted()) doc.setAllSecurityToBeRemoved(true);
      doc.save(out);
      return out.toByteArray();
    }
  }
}
