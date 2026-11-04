package cn.haiyan.platform.web;

import cn.haiyan.platform.service.CommunityStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class TrafficController {
    private final CommunityStore db;
    // Traffic counts tolerate a short delay; avoid a full-table count per visitor heartbeat.
    private volatile CachedTraffic cached;
    private record CachedTraffic(Traffic value,long expiresAt) {}
    private static final long COUNT_TTL_NANOS=Duration.ofSeconds(10).toNanos();
    public TrafficController(CommunityStore db) { this.db=db; }
    public record Traffic(long online,long visitors) {}

    @PostMapping("/api/public/traffic")
    @Transactional
    public Traffic heartbeat(@CookieValue(name="haiyan_visitor",defaultValue="") String visitor,
                             HttpServletRequest request,HttpServletResponse response) {
        if(!visitor.matches("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")) {
            visitor=UUID.randomUUID().toString();
            response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("haiyan_visitor",visitor)
                .httpOnly(true).secure(request.isSecure()).sameSite("Lax").path("/").maxAge(Duration.ofDays(365)).build().toString());
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        db.update("INSERT INTO site_visitors(visitor_hash) VALUES(?) ON DUPLICATE KEY UPDATE last_seen=CURRENT_TIMESTAMP",AuthController.hash(visitor));
        return counts();
    }
    private Traffic counts(){
        long now=System.nanoTime();CachedTraffic current=cached;
        if(current!=null&&now-current.expiresAt()<0)return current.value();
        synchronized(this){
            now=System.nanoTime();current=cached;
            if(current!=null&&now-current.expiresAt()<0)return current.value();
            var counts=db.one("SELECT COUNT(*) AS visitors,COALESCE(SUM(last_seen>=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 90 SECOND)),0) AS online FROM site_visitors");
            var result=new Traffic(((Number)counts.get("online")).longValue(),((Number)counts.get("visitors")).longValue());
            cached=new CachedTraffic(result,System.nanoTime()+COUNT_TTL_NANOS);return result;
        }
    }
}
