package com.officebox.controller;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

@RestController
@RequestMapping("/api/pdf")
public class PdfWatermarkController {
    @PostMapping(value = "/watermark", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> watermark(@RequestParam("file") MultipartFile file,
                                            @RequestParam("text") String text,
                                            @RequestParam(defaultValue = "0.18") float opacity,
                                            @RequestParam(defaultValue = "45") float rotation,
                                            @RequestParam(defaultValue = "48") float fontSize) throws Exception {
        if (file == null || file.isEmpty() || text == null || text.isBlank()) return ResponseEntity.badRequest().build();
        opacity = Math.max(0.05f, Math.min(opacity, 1f));
        fontSize = Math.max(8f, Math.min(fontSize, 120f));
        try (PDDocument doc = PDDocument.load(file.getBytes()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDExtendedGraphicsState state = new PDExtendedGraphicsState();
            state.setNonStrokingAlphaConstant(opacity);
            for (PDPage page : doc.getPages()) {
                PDRectangle box = page.getMediaBox();
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setGraphicsStateParameters(state);
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), fontSize);
                    float x = box.getWidth() / 2f;
                    float y = box.getHeight() / 2f;
                    cs.setTextMatrix((float)Math.cos(Math.toRadians(rotation)), (float)Math.sin(Math.toRadians(rotation)),
                            (float)-Math.sin(Math.toRadians(rotation)), (float)Math.cos(Math.toRadians(rotation)), x, y);
                    cs.showText(text.length() > 180 ? text.substring(0, 180) : text);
                    cs.endText();
                }
            }
            doc.save(out);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header("Content-Disposition", "attachment; filename=\"officebox-watermarked.pdf\"").body(out.toByteArray());
        }
    }
}
