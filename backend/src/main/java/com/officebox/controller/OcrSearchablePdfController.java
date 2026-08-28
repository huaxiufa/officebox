package com.officebox.controller;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;import java.awt.image.BufferedImage;import java.io.*;import java.nio.file.*;import java.util.Comparator;import java.util.concurrent.TimeUnit;
@RestController @RequestMapping("/api/ocr")
public class OcrSearchablePdfController {
 @PostMapping(value="/searchable-pdf",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public ResponseEntity<byte[]> searchablePdf(@RequestParam("file") MultipartFile file,@RequestParam(defaultValue="chi_sim+eng") String lang)throws Exception{
  if(file==null||file.isEmpty())return ResponseEntity.badRequest().build();Path dir=Files.createTempDirectory("officebox-searchable-");
  try(PDDocument source=Loader.loadPDF(file.getBytes());PDDocument result=new PDDocument();ByteArrayOutputStream out=new ByteArrayOutputStream()){
   PDFRenderer renderer=new PDFRenderer(source);int pages=Math.min(source.getNumberOfPages(),50);
   for(int i=0;i<pages;i++){BufferedImage image=renderer.renderImageWithDPI(i,150);Path png=dir.resolve("p"+i+".png"),base=dir.resolve("p"+i);ImageIO.write(image,"png",png.toFile());Process p=new ProcessBuilder("tesseract",png.toString(),base.toString(),"-l",safeLang(lang),"--psm","3","pdf").redirectErrorStream(true).start();if(!p.waitFor(60,TimeUnit.SECONDS)||p.exitValue()!=0)continue;Path ocrPdf=Path.of(base+".pdf");if(Files.exists(ocrPdf)){try(PDDocument ocr=Loader.loadPDF(ocrPdf.toFile())){for(PDPage page:ocr.getPages())result.importPage(page);}}}
   if(result.getNumberOfPages()==0)return ResponseEntity.badRequest().build();result.save(out);return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header("Content-Disposition","attachment; filename=\"officebox-searchable.pdf\"").body(out.toByteArray());
  }finally{try(var s=Files.walk(dir)){s.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}}
 }
 private String safeLang(String lang){return lang.matches("[a-zA-Z_+]+")?lang:"chi_sim+eng";}
}
