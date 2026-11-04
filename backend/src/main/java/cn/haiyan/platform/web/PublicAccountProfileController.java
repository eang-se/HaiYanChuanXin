package cn.haiyan.platform.web;

import cn.haiyan.platform.service.CommunityStore;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static cn.haiyan.platform.service.CommunityService.*;

@RestController
public class PublicAccountProfileController {
 private final CommunityStore db;
 private final Path root;
 @org.springframework.beans.factory.annotation.Autowired
 public PublicAccountProfileController(CommunityStore db){this(db,Path.of("data","avatars").toAbsolutePath().normalize());}
 PublicAccountProfileController(CommunityStore db,Path root){this.db=db;this.root=root.toAbsolutePath().normalize();}
 public record Profile(String penName,String bio){}

 @PutMapping("/api/admin/public-accounts/{id}/profile")
 public void update(@PathVariable long id,@RequestBody Profile input){
  String name=bounded(input.penName(),40,"账号昵称"),bio=bounded(input.bio(),500,"简介");
  if(name.isBlank())throw error(400,"请填写账号昵称");
  if(db.update("UPDATE members SET pen_name=?,bio=?,signature='' WHERE id=? AND account_type='PUBLIC' AND deleted_at IS NULL",name,bio,id)==0
      &&db.count("SELECT COUNT(*) FROM members WHERE id=? AND account_type='PUBLIC' AND deleted_at IS NULL",id)==0)throw error(404,"公用账号不存在");
 }

 @PostMapping("/api/admin/public-accounts/{id}/avatar")
 public Map<String,String> upload(@PathVariable long id,@RequestParam MultipartFile file) throws IOException {
  if(db.count("SELECT COUNT(*) FROM members WHERE id=? AND account_type='PUBLIC' AND deleted_at IS NULL",id)==0)throw error(404,"公用账号不存在");
  if(file.isEmpty()||file.getSize()>5*1024*1024)throw error(400,"头像须为 5 MB 以内的 PNG 或 JPEG 图片");
  BufferedImage source;
  try(var stream=ImageIO.createImageInputStream(file.getInputStream())){
   var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw error(400,"无法识别图片，请上传 PNG 或 JPEG");
   var reader=readers.next();try{
    String format=reader.getFormatName();if(!format.equalsIgnoreCase("png")&&!format.equalsIgnoreCase("jpeg"))throw error(400,"仅支持 PNG、JPEG 图片");
    reader.setInput(stream);int width=reader.getWidth(0),height=reader.getHeight(0);
    if(width<1||height<1||width>8000||height>8000||(long)width*height>20000000)throw error(400,"图片尺寸过大，请缩小后上传");
    source=reader.read(0);if(source==null)throw error(400,"无法识别图片");
   }finally{reader.dispose();}
  }
  double scale=Math.min(1,800.0/Math.max(source.getWidth(),source.getHeight()));
  var output=new BufferedImage(Math.max(1,(int)(source.getWidth()*scale)),Math.max(1,(int)(source.getHeight()*scale)),BufferedImage.TYPE_INT_RGB);
  var graphics=output.createGraphics();graphics.setColor(Color.WHITE);graphics.fillRect(0,0,output.getWidth(),output.getHeight());graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);graphics.drawImage(source,0,0,output.getWidth(),output.getHeight(),null);graphics.dispose();
  Files.createDirectories(root);String avatarId=UUID.randomUUID().toString(),url="/api/public/account-avatars/"+avatarId;Path target=root.resolve(avatarId+".jpg");
  try{if(!ImageIO.write(output,"jpeg",target.toFile()))throw new IOException("JPEG encoder unavailable");
   if(db.update("UPDATE members SET avatar_url=? WHERE id=? AND account_type='PUBLIC' AND deleted_at IS NULL",url,id)!=1)throw error(404,"公用账号不存在");
  }catch(IOException|RuntimeException ex){Files.deleteIfExists(target);throw ex;}
  return Map.of("avatarUrl",url);
 }

 @GetMapping("/api/public/account-avatars/{id}")
 public ResponseEntity<FileSystemResource> avatar(@PathVariable String id){
  if(!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))throw error(404,"头像不存在");
  String url="/api/public/account-avatars/"+id;
  if(db.count("SELECT COUNT(*) FROM members WHERE avatar_url=? AND account_type='PUBLIC' AND deleted_at IS NULL",url)==0)throw error(404,"头像不存在");
  Path file=root.resolve(id+".jpg");if(!Files.isRegularFile(file))throw error(404,"头像不存在");
  return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noCache().cachePublic()).body(new FileSystemResource(file));
 }
}
