package com.officebox.image;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

@RestController
@RequestMapping("/api/image/transform")
@CrossOrigin(origins = "*")
public class ImageTransformController {
  @PostMapping(value="/crop", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> crop(@RequestPart MultipartFile file,@RequestParam int x,@RequestParam int y,@RequestParam int width,@RequestParam int height) throws IOException {
    BufferedImage src=read(file); if(width<=0||height<=0||x<0||y<0||x+width>src.getWidth()||y+height>src.getHeight()) throw new IllegalArgumentException("裁剪区域超出图片范围");
    return output(src.getSubimage(x,y,width,height), ext(file));
  }
  @PostMapping(value="/flip", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> flip(@RequestPart MultipartFile file,@RequestParam(defaultValue="horizontal") String direction) throws IOException {
    BufferedImage src=read(file), out=new BufferedImage(src.getWidth(),src.getHeight(),BufferedImage.TYPE_INT_ARGB); Graphics2D g=out.createGraphics();
    if("vertical".equalsIgnoreCase(direction)){g.scale(1,-1);g.translate(0,-src.getHeight());}else{g.scale(-1,1);g.translate(-src.getWidth(),0);} g.drawImage(src,0,0,null);g.dispose(); return output(out,ext(file));
  }
  private BufferedImage read(MultipartFile f)throws IOException{BufferedImage i=ImageIO.read(f.getInputStream());if(i==null)throw new IllegalArgumentException("不支持的图片格式");return i;}
  private String ext(MultipartFile f){String n=f.getOriginalFilename()==null?"":f.getOriginalFilename().toLowerCase();return n.endsWith(".png")?"png":"jpg";}
  private ResponseEntity<byte[]> output(BufferedImage i,String fmt)throws IOException{if(fmt.equals("jpg")){BufferedImage r=new BufferedImage(i.getWidth(),i.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=r.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,r.getWidth(),r.getHeight());g.drawImage(i,0,0,null);g.dispose();i=r;}ByteArrayOutputStream b=new ByteArrayOutputStream();ImageIO.write(i,fmt,b);return ResponseEntity.ok().contentType(MediaType.parseMediaType("image/"+fmt)).body(b.toByteArray());}
}
