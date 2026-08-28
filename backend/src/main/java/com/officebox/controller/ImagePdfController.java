package com.officebox.controller;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/image")
public class ImagePdfController {
    @PostMapping(value="/to-pdf", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> toPdf(@RequestParam("files") MultipartFile[] files) throws Exception {
        if(files==null||files.length==0) return ResponseEntity.badRequest().build();
        try(PDDocument doc=new PDDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            for(MultipartFile file:files){
                BufferedImage image=ImageIO.read(file.getInputStream());
                if(image==null) continue;
                PDPage page=new PDPage(new PDRectangle(image.getWidth(),image.getHeight())); doc.addPage(page);
                PDImageXObject x=LosslessFactory.createFromImage(doc,image);
                try(PDPageContentStream cs=new PDPageContentStream(doc,page)){cs.drawImage(x,0,0,image.getWidth(),image.getHeight());}
            }
            if(doc.getNumberOfPages()==0) return ResponseEntity.badRequest().build();
            doc.save(out); return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Content-Disposition","attachment; filename=\"officebox-images.pdf\"").body(out.toByteArray());
        }
    }

    @PostMapping(value="/to-png", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> toPng(@RequestParam("file") MultipartFile file) throws Exception {
        if(file==null||file.isEmpty()) return ResponseEntity.badRequest().build();
        try(PDDocument doc=PDDocument.load(file.getBytes()); ByteArrayOutputStream zipOut=new ByteArrayOutputStream(); ZipOutputStream zip=new ZipOutputStream(zipOut)){
            var renderer=new org.apache.pdfbox.rendering.PDFRenderer(doc);
            for(int i=0;i<doc.getNumberOfPages();i++){BufferedImage image=renderer.renderImageWithDPI(i,150);ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(image,"png",png);zip.putNextEntry(new ZipEntry("page-"+(i+1)+".png"));zip.write(png.toByteArray());zip.closeEntry();}
            zip.finish(); return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).header("Content-Disposition","attachment; filename=\"officebox-images.zip\"").body(zipOut.toByteArray());
        }
    }
}
