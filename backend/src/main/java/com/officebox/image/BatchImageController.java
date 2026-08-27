package com.officebox.image;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
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
        if(out.equals("jpg")){ BufferedImage rgb=new BufferedImage(img.getWidth(),img.getHeight(),BufferedImage.TYPE_INT_RGB); Graphics2D rg=rgb.createGraphics(); rg.setColor(Color.WHITE); rg.fillRect(0,0,rgb.getWidth(),rgb.getHeight()); rg.drawImage(img,0,0,null); rg.dispose(); img=rgb; }
        ByteArrayOutputStream imageBytes=new ByteArrayOutputStream();
        ImageIO.write(img, out.equals("webp")?"png":out, imageBytes);
        String name=safeName(files[i].getOriginalFilename(), i)+"."+out;
        zip.putNextEntry(new ZipEntry(name)); zip.write(imageBytes.toByteArray()); zip.closeEntry();
      }
    }
    return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
        .header("Content-Disposition","attachment; filename=officebox-images.zip").body(zipBytes.toByteArray());
  }
  private String normalize(String f){String s=f.toLowerCase().replace(".",""); return s.equals("png")?"png":s.equals("webp")?"webp":"jpg";}
  private String safeName(String n,int i){ if(n==null||n.isBlank()) return "image-"+(i+1); int p=n.lastIndexOf('.'); return (p>0?n.substring(0,p):n).replaceAll("[^\\p{L}\\p{N}_-]","_"); }
  private BufferedImage rotate(BufferedImage src,int angle){int nw=(angle==90||angle==270)?src.getHeight():src.getWidth(),nh=(angle==90||angle==270)?src.getWidth():src.getHeight();BufferedImage dst=new BufferedImage(nw,nh,BufferedImage.TYPE_INT_ARGB);Graphics2D g=dst.createGraphics();if(angle==90)g.translate(nw,0);else if(angle==180)g.translate(nw,nh);else if(angle==270)g.translate(0,nh);g.rotate(Math.toRadians(angle));g.drawImage(src,0,0,null);g.dispose();return dst;}
}
