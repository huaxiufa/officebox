package com.officebox.image;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@RestController
@RequestMapping("/api/image")
@CrossOrigin(origins = "*")
public class ImageController {
  @PostMapping(value = "/resize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> resize(@RequestPart("file") MultipartFile file, @RequestParam int width, @RequestParam int height) throws IOException {
    if (width < 1 || height < 1 || width > 10000 || height > 10000) throw new IllegalArgumentException("图片尺寸无效");
    BufferedImage src = ImageIO.read(file.getInputStream());
    if (src == null) throw new IllegalArgumentException("无法读取图片");
    BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = dst.createGraphics(); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(src, 0, 0, width, height, null); g.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(dst, "jpg", out);
    return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).header("Content-Disposition", "attachment; filename=officebox-resized.jpg").body(out.toByteArray());
  }

  @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> convert(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "jpg") String format) throws IOException {
    if (!format.matches("png|jpg|jpeg|webp")) throw new IllegalArgumentException("不支持的图片格式");
    BufferedImage image = ImageIO.read(file.getInputStream()); if (image == null) throw new IllegalArgumentException("无法读取图片");
    if (format.equals("webp")) format = "png";
    ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image, format.equals("jpeg") ? "jpg" : format, out);
    MediaType type = format.equals("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
    return ResponseEntity.ok().contentType(type).header("Content-Disposition", "attachment; filename=officebox-converted." + format).body(out.toByteArray());
  }
}
