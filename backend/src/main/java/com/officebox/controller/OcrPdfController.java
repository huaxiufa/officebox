package com.officebox.controller;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;import java.nio.file.*;import java.util.concurrent.TimeUnit;
@RestController @RequestMapping("/api/ocr")
public class OcrPdfController {
 @PostMapping(value="/pdf",consumes=MediaType.MULTIPART_FORM_DATA_VALUE,produces=MediaType.TEXT_PLAIN_VALUE)
 public ResponseEntity<String> pdf(@RequestParam("file") MultipartFile file,@RequestParam(defaultValue="chi_sim+eng") String lang)throws Exception{
  if(file==null||file.isEmpty())return ResponseEntity.badRequest().build();Path dir=Files.createTempDirectory("officebox-ocr-");
  try(PDDocument doc=Loader.loadPDF(file.getBytes())){PDFRenderer renderer=new PDFRenderer(doc);StringBuilder text=new StringBuilder();int pages=Math.min(doc.getNumberOfPages(),50);
   for(int i=0;i<pages;i++){Path image=dir.resolve("page-"+i+".png"),out=dir.resolve("page-"+i+".txt");ImageIO.write(renderer.renderImageWithDPI(i,150),"png",image.toFile());Process p=new ProcessBuilder("tesseract",image.toString(),out.toString().replace(".txt",""),"-l",safeLang(lang),"--psm","3").redirectErrorStream(true).start();if(!p.waitFor(60,TimeUnit.SECONDS)){p.destroyForcibly();continue;}if(p.exitValue()==0&&Files.exists(out))text.append("\n--- 第 ").append(i+1).append(" 页 ---\n").append(Files.readString(out));}return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(text.toString().trim());
  }finally{try(var s=Files.walk(dir)){s.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}}
 }
 private String safeLang(String lang){return lang.matches("[a-zA-Z_+]+")?lang:"chi_sim+eng";}
}
