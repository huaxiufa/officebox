package com.officebox.pdf;
import org.apache.pdfbox.Loader;import org.apache.pdfbox.pdmodel.PDDocument;import org.springframework.http.MediaType;import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;import java.io.ByteArrayOutputStream;
@RestController @RequestMapping("/api/pdf") @CrossOrigin(origins="*")
public class PdfCompressController{
 @PostMapping(value="/compress",consumes=MediaType.MULTIPART_FORM_DATA_VALUE,produces=MediaType.APPLICATION_PDF_VALUE) public byte[] compress(@RequestPart("file")MultipartFile file)throws Exception{if(file==null||file.isEmpty())throw new IllegalArgumentException("PDF 文件不能为空");try(PDDocument doc=Loader.loadPDF(file.getBytes());ByteArrayOutputStream out=new ByteArrayOutputStream()){doc.save(out);return out.toByteArray();}}
}
