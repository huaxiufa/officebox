package com.officebox.controller;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/pdf")
public class PdfPageController {
    @PostMapping(value = "/extract-pages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> extractPages(@RequestParam("file") MultipartFile file, @RequestParam("pages") String pages) throws Exception {
        if (file == null || file.isEmpty() || pages == null || pages.isBlank()) return ResponseEntity.badRequest().build();
        try (PDDocument source = Loader.loadPDF(file.getBytes()); ByteArrayOutputStream zipOut = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(zipOut)) {
            List<Integer> indexes = parsePages(pages, source.getNumberOfPages()); if (indexes.isEmpty()) return ResponseEntity.badRequest().build();
            for (int index : indexes) try (PDDocument one = new PDDocument()) {
                one.importPage(source.getPage(index)); ByteArrayOutputStream pdf = new ByteArrayOutputStream(); one.save(pdf);
                zip.putNextEntry(new ZipEntry("page-" + (index + 1) + ".pdf")); zip.write(pdf.toByteArray()); zip.closeEntry();
            }
            zip.finish();
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).header("Content-Disposition", "attachment; filename=\"officebox-pages.zip\"").body(zipOut.toByteArray());
        }
    }

    @PostMapping(value = "/delete-pages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> deletePages(@RequestParam("file") MultipartFile file, @RequestParam("pages") String pages) throws Exception {
        if (file == null || file.isEmpty() || pages == null || pages.isBlank()) return ResponseEntity.badRequest().build();
        try (PDDocument doc = Loader.loadPDF(file.getBytes()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<Integer> indexes = parsePages(pages, doc.getNumberOfPages()); indexes.sort(Comparator.reverseOrder());
            for (int index : indexes) doc.removePage(index); doc.save(out);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Content-Disposition", "attachment; filename=\"officebox-pages-removed.pdf\"").body(out.toByteArray());
        }
    }
    private List<Integer> parsePages(String text, int total) { Set<Integer> result = new TreeSet<>(); for (String token : text.split(",")) { token = token.trim(); if (token.matches("\\d+")) add(result, Integer.parseInt(token), total); else if (token.matches("\\d+[-~]\\d+")) { String[] p = token.split("[-~]"); int a=Integer.parseInt(p[0]), b=Integer.parseInt(p[1]); if(a>b){int t=a;a=b;b=t;} for(int n=a;n<=b&&n<=total;n++) add(result,n,total); } } return new ArrayList<>(result); }
    private void add(Set<Integer> result, int page, int total) { if(page>=1&&page<=total) result.add(page-1); }
}
