package cn.haiyan.platform.web;
import cn.haiyan.platform.service.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static cn.haiyan.platform.service.CommunityService.*;

@RestController
public class UploadController {
    private final CommunityService s; private final AccountPermissions permissions;private final Semaphore parsers=new Semaphore(2),images=new Semaphore(1);
    private final Path root=Path.of("data","images").toAbsolutePath().normalize();
    public UploadController(CommunityService s,AccountPermissions permissions){this.s=s;this.permissions=permissions;}
    @PostMapping("/api/member/images")public Map<String,String> image(@RequestParam MultipartFile file,Principal p)throws IOException{
        permissions.require(p.getName(),"UPLOAD");
        if(file.isEmpty()||file.getSize()>5*1024*1024)throw error(400,"图片需在 5 MB 以内，支持 PNG、JPEG");
        if(!images.tryAcquire())throw error(429,"已有图片正在处理，请稍后重试");
        try {
        BufferedImage source;
        try(var stream=ImageIO.createImageInputStream(file.getInputStream())) {
            var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw error(400,"无法识别图片，请上传 PNG 或 JPEG");
            var reader=readers.next();try{
                String format=reader.getFormatName();if(!format.equalsIgnoreCase("png")&&!format.equalsIgnoreCase("jpeg"))throw error(400,"仅支持 PNG、JPEG 图片");
                reader.setInput(stream);int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width>8000||height>8000||(long)width*height>20000000)throw error(400,"图片尺寸过大，请缩小后上传");
                source=reader.read(0);
            }finally{reader.dispose();}
        }
        double scale=Math.min(1,1600.0/Math.max(source.getWidth(),source.getHeight()));
        BufferedImage output=new BufferedImage(Math.max(1,(int)(source.getWidth()*scale)),Math.max(1,(int)(source.getHeight()*scale)),BufferedImage.TYPE_INT_RGB);
        var graphics=output.createGraphics();graphics.setColor(Color.WHITE);graphics.fillRect(0,0,output.getWidth(),output.getHeight());graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);graphics.drawImage(source,0,0,output.getWidth(),output.getHeight(),null);graphics.dispose();
        String id=UUID.randomUUID().toString(),name=id+".jpg";Files.createDirectories(root);Path target=root.resolve(name);
        ImageIO.write(output,"jpg",target.toFile());
        try{s.store().update("INSERT INTO media_files(id,owner_id,original_name,file_path,mime_type,size_bytes) VALUES(?,?,?,?,?,?)",id,s.memberId(p.getName()),bounded(file.getOriginalFilename(),200,"文件名"),name,"image/jpeg",Files.size(target));}
        catch(RuntimeException ex){Files.deleteIfExists(target);throw ex;}
        return Map.of("url","/api/media/"+id,"width",String.valueOf(output.getWidth()),"height",String.valueOf(output.getHeight()));
        } finally { images.release(); }
    }
    @GetMapping("/api/media/{id}")public ResponseEntity<FileSystemResource> media(@PathVariable String id,Principal p){
        var row=s.store().one("SELECT * FROM media_files WHERE id=?",id);if(row==null)throw error(404,"图片不存在");
        boolean own=p!=null&&Objects.equals(row.get("ownerId"),s.member(p.getName()).get("id"));
        if(!own&&s.store().count("SELECT COUNT(*) FROM work_media wm JOIN works w ON w.id=wm.work_id WHERE wm.media_id=? AND w.status='PUBLISHED'",id)==0)throw error(404,"图片尚未公开");
        Path target=root.resolve((String)row.get("filePath")).normalize();if(!target.startsWith(root))throw error(404,"图片不存在");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).body(new FileSystemResource(target));
    }
    @PostMapping("/api/member/import")public Map<String,String> importFile(@RequestParam MultipartFile file,Principal principal)throws IOException{
        permissions.require(principal.getName(),"UPLOAD");
        if(file.isEmpty()||file.getSize()>20*1024*1024)throw error(400,"文档需在 20 MB 以内");
        if(!parsers.tryAcquire())throw error(429,"已有文档正在解析，请稍后再试");
        try{
            String name=Objects.requireNonNullElse(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT),text;
            if(name.endsWith(".docx")){try(var doc=new XWPFDocument(file.getInputStream());var extract=new XWPFWordExtractor(doc)){text=extract.getText();}}
            else if(name.endsWith(".pdf")){try(var doc=Loader.loadPDF(file.getBytes())){if(doc.getNumberOfPages()>100)throw error(400,"PDF 请控制在 100 页以内");text=new PDFTextStripper().getText(doc);}}
            else if(name.endsWith(".txt")){text=new String(file.getBytes(),StandardCharsets.UTF_8);if(text.contains("\uFFFD"))throw error(400,"请将文本另存为 UTF-8 编码后导入");}
            else throw error(400,"支持 DOCX、文字型 PDF 和 UTF-8 TXT");
            if(text.isBlank())throw error(400,"未提取到文字；扫描件需要先进行 OCR");
            if(text.length()>100000)throw error(400,"导入文字超过 10 万字符，请分篇提交");
            return Map.of("text",text,"name",Objects.requireNonNullElse(file.getOriginalFilename(),"导入文档"));
        }catch(org.springframework.web.server.ResponseStatusException ex){throw ex;}
        catch(Exception ex){throw error(400,"无法解析此文件，请确认文件格式正确且未加密");}
        finally{parsers.release();}
    }
}
