package cn.haiyan.platform.web;

import cn.haiyan.platform.service.CommunityStore;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static cn.haiyan.platform.service.CommunityService.*;

@RestController
public class QqAvatarController {
    private final CommunityStore db;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Semaphore requests=new Semaphore(12);
    public QqAvatarController(CommunityStore db){this.db=db;}
    @GetMapping("/api/public/avatars/{id}")
    public ResponseEntity<byte[]> avatar(@PathVariable long id) {
        var member=db.one("SELECT qq_number FROM members WHERE id=?",id);
        Object value=member==null?null:member.get("qqNumber");
        if(!(value instanceof String qq)||!qq.matches("[1-9][0-9]{4,11}"))throw error(404,"头像不存在");
        if(!requests.tryAcquire())throw error(503,"头像服务繁忙");
        try {
            var request=HttpRequest.newBuilder(URI.create("https://q1.qlogo.cn/g?b=qq&nk="+qq+"&s=100")).timeout(Duration.ofSeconds(5)).GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(InputStream body=response.body()) {
                if(response.statusCode()!=200 || response.headers().firstValueAsLong("content-length").orElse(0)>1024*1024)throw error(502,"头像暂不可用");
                byte[] bytes=body.readNBytes(1024*1024+1);
                if(bytes.length<3||bytes.length>1024*1024||(bytes[0]&255)!=255||(bytes[1]&255)!=216||(bytes[2]&255)!=255)throw error(502,"头像暂不可用");
                return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).body(bytes);
            }
        } catch(InterruptedException ex){Thread.currentThread().interrupt();throw error(503,"头像暂不可用");}
        catch(java.io.IOException ex){throw error(502,"头像暂不可用");}
        finally{requests.release();}
    }
}
