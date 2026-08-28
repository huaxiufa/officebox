package com.officebox.image;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/image/batch")
@CrossOrigin(origins = "*")
public class BatchImageController {
  @PostMapping(value="/process", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> process(@RequestPart("files") MultipartFile[] files,
      @RequestParam(defaultValue="jpg") String format,
      @RequestParam(defaultValue="85") int quality,
      @RequestParam(defaultValue="0") int width,
      @RequestParam(defaultValue="0") int height,
      @RequestParam(defaultValue="0") int rotate) throws IOException {
    if (files == null || files.length == 0 || files.length > 50) throw new IllegalArgumentException("一次最多处理 50 张图片");
    if (quality < 10 || quality > 100) throw new IllegalArgumentException("质量必须为 10-100");
    String out = normalize(format);
    ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(zipBytes)) {
      for (int i=0;i<files.length;i++) {
        BufferedImage source=ImageIO.read(files[i].getInputStream());
        if(source==null) continue;
        int w=width>0?width:source.getWidth(), h=height>0?height:source.getHeight();
        if(width>0 && height==0) h=Math.max(1,(int)Math.round(source.getHeight()*(w/(double)source.getWidth())));
        if(height>0 && width==0) w=Math.max(1,(int)Math.round(source.getWidth()*(h/(double)source.getHeight())));
        BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=img.createGraphics(); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR); g.drawImage(source,0,0,w,h,null); g.dispose();
        int angle=((rotate%360)+360)%360;
        if(angle!=0) img=rotate(img,angle);
        if(out.equals("jpg")) img=toRgb(img);
        ByteArrayOutputStream imageBytes=new ByteArrayOutputStream();
        if(out.equals("jpg")) writeJpeg(img, imageBytes, quality / 100f);
        else ImageIO.write(img, out, imageBytes);
        String name=safeName(files[i].getOriginalFilename(), i)+"."+out;
        zip.putNextEntry(new ZipEntry(name)); zip.write(imageBytes.toByteArray()); zip.closeEntry();
      }
    }
    return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
        .header("Content-Disposition","attachment; filename=officebox-images.zip").body(zipBytes.toByteArray());
  }
  private String normalize(String f){String s=f.toLowerCase().replace(".",""); return s.equals("png")?"png":"jpg";}
  private BufferedImage toRgb(BufferedImage img){BufferedImage rgb=new BufferedImage(img.getWidth(),img.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=rgb.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,rgb.getWidth(),rgb.getHeight());g.drawImage(img,0,0,null);g.dispose();return rgb;}
  private void writeJpeg(BufferedImage img, OutputStream out, float quality) throws IOException {Iterator<ImageWriter> writers=ImageIO.getImageWritersByFormatName("jpg");if(!writers.hasNext()) throw new IOException("JPEG 编码器不可用");ImageWriter writer=writers.next();try(ImageOutputStream ios=ImageIO.createImageOutputStream(out)){writer.setOutput(ios);ImageWriteParam p=writer.getDefaultWriteParam();if(p.canWriteCompressed()){p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);p.setCompressionQuality(Math.max(.1f,Math.min(1f,quality)));}writer.write(null,new IIOImage(img,null,null),p);}finally{writer.dispose();}}
  private String safeName(String n,int i){if(n==null||n.isBlank())return "image-"+(i+1);int p=n.lastIndexOf('.');return (p>0?n.substring(0,p):n).replaceAll("[^\\p{L}\\p{N}_-]","_");}
  private BufferedImage rotate(BufferedImage src,int angle){int nw=(angle==90||angle==270)?src.getHeight():src.getWidth(),nh=(angle==90||angle==270)?src.getWidth():src.getHeight();BufferedImage dst=new BufferedImage(nw,nh,BufferedImage.TYPE_INT_ARGB);Graphics2D g=dst.createGraphics();if(angle==90)g.translate(nw,0);else if(angle==180)g.translate(nw,nh);else if(angle==270)g.translate(0,nh);g.rotate(Math.toRadians(angle));g.drawImage(src,0,0,null);g.dispose();return dst;}
}
