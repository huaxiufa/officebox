package com.officebox.image;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Locale;

@RestController
@RequestMapping("/api/image")
@CrossOrigin(origins = "*")
public class ImageController {
  @PostMapping(value="/process", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> process(@RequestPart("file") MultipartFile file,
      @RequestParam(defaultValue="jpg") String format, @RequestParam(defaultValue="90") int quality,
      @RequestParam(defaultValue="0") int width, @RequestParam(defaultValue="0") int height,
      @RequestParam(defaultValue="0") int rotate) throws IOException {
    BufferedImage source=ImageIO.read(file.getInputStream());
    if(source==null) throw new IllegalArgumentException("无法读取图片");
    if(width<0||height<0||width>10000||height>10000) throw new IllegalArgumentException("图片尺寸无效");
    int w=width>0?width:source.getWidth(), h=height>0?height:source.getHeight();
    if(width>0 && height==0) h=Math.max(1,(int)Math.round(source.getHeight()*(w/(double)source.getWidth())));
    if(height>0 && width==0) w=Math.max(1,(int)Math.round(source.getWidth()*(h/(double)source.getHeight())));
    BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
    Graphics2D g=img.createGraphics(); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR); g.drawImage(source,0,0,w,h,null); g.dispose();
    int angle=((rotate%360)+360)%360;
    if(angle!=0) img=rotate(img,angle);
    String out=normalize(format);
    if(out.equals("jpg")){ BufferedImage rgb=new BufferedImage(img.getWidth(),img.getHeight(),BufferedImage.TYPE_INT_RGB); Graphics2D rg=rgb.createGraphics(); rg.setColor(Color.WHITE); rg.fillRect(0,0,rgb.getWidth(),rgb.getHeight()); rg.drawImage(img,0,0,null); rg.dispose(); img=rgb; }
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    if(!ImageIO.write(img,out.equals("webp")?"png":out,bytes)) throw new IllegalArgumentException("输出格式不可用");
    MediaType type=out.equals("png")?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG;
    return ResponseEntity.ok().contentType(type).header("Content-Disposition","attachment; filename=officebox-image."+out).body(bytes.toByteArray());
  }
  private String normalize(String f){ String s=f.toLowerCase(Locale.ROOT).replace(".",""); return s.equals("png")?"png":s.equals("webp")?"webp":"jpg"; }
  private BufferedImage rotate(BufferedImage src,int angle){ int nw=(angle==90||angle==270)?src.getHeight():src.getWidth(), nh=(angle==90||angle==270)?src.getWidth():src.getHeight(); BufferedImage dst=new BufferedImage(nw,nh,BufferedImage.TYPE_INT_ARGB); Graphics2D g=dst.createGraphics(); if(angle==90)g.translate(nw,0); else if(angle==180)g.translate(nw,nh); else if(angle==270)g.translate(0,nh); g.rotate(Math.toRadians(angle)); g.drawImage(src,0,0,null); g.dispose(); return dst; }

  @PostMapping(value = "/resize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> resize(@RequestPart("file") MultipartFile file,@RequestParam int width,@RequestParam int height) throws IOException { return process(file,"jpg",90,width,height,0); }
  @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> convert(@RequestPart("file") MultipartFile file,@RequestParam(defaultValue="jpg") String format) throws IOException { return process(file,format,90,0,0,0); }
}
