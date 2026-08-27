package com.officebox.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/pdf")
@CrossOrigin(origins = "*")
public class PdfOperationsController {

  @PostMapping(value = "/split", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
  public byte[] split(@RequestPart("file") MultipartFile file, @RequestParam int page) throws IOException {
    try (PDDocument source = Loader.loadPDF(file.getBytes()); PDDocument out = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      int index = page - 1;
      if (index < 0 || index >= source.getNumberOfPages()) throw new IllegalArgumentException("页码超出范围");
      out.addPage(out.importPage(source.getPage(index)));
      out.save(bytes);
      return bytes.toByteArray();
    }
  }

  @PostMapping(value = "/delete-pages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
  public byte[] deletePages(@RequestPart("file") MultipartFile file, @RequestParam String pages) throws IOException {
    try (PDDocument doc = Loader.loadPDF(file.getBytes()); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      List<Integer> indexes = parsePages(pages);
      indexes.sort((a, b) -> Integer.compare(b, a));
      for (int page : indexes) {
        if (page < 1 || page > doc.getNumberOfPages()) throw new IllegalArgumentException("页码超出范围: " + page);
        doc.removePage(page - 1);
      }
      if (doc.getNumberOfPages() == 0) throw new IllegalArgumentException("不能删除全部页面");
      doc.save(bytes);
      return bytes.toByteArray();
    }
  }

  @PostMapping(value = "/rotate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
  public byte[] rotate(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "90") int degrees) throws IOException {
    if (degrees % 90 != 0) throw new IllegalArgumentException("旋转角度必须是 90 的倍数");
    try (PDDocument doc = Loader.loadPDF(file.getBytes()); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      for (PDPage p : doc.getPages()) p.setRotation((p.getRotation() + degrees) % 360);
      doc.save(bytes);
      return bytes.toByteArray();
    }
  }

  private List<Integer> parsePages(String value) {
    List<Integer> pages = new ArrayList<>();
    for (String token : value.split(",")) {
      token = token.trim();
      if (token.contains("-")) {
        String[] range = token.split("-", 2);
        int start = Integer.parseInt(range[0].trim()), end = Integer.parseInt(range[1].trim());
        if (start > end) { int t = start; start = end; end = t; }
        for (int i = start; i <= end; i++) pages.add(i);
      } else if (!token.isBlank()) pages.add(Integer.parseInt(token));
    }
    return pages.stream().distinct().toList();
  }
}
