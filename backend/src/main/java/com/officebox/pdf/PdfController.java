package com.officebox.pdf;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.*;

@RestController
@RequestMapping("/api/pdf")
@CrossOrigin(origins = "*")
public class PdfController {
  @PostMapping(value = "/merge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
  public byte[] merge(@RequestParam("files") List<MultipartFile> files) throws IOException {
    if (files == null || files.size() < 2) throw new IllegalArgumentException("至少需要两个 PDF 文件");
    PDFMergerUtility merger = new PDFMergerUtility();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    merger.setDestinationStream(out);
    for (MultipartFile file : files) {
      if (!Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase().endsWith(".pdf")) throw new IllegalArgumentException("仅支持 PDF 文件");
      merger.addSource(file.getInputStream());
    }
    merger.mergeDocuments(null);
    return out.toByteArray();
  }
}
