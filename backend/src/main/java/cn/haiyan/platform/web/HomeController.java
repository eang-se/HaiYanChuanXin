package cn.haiyan.platform.web;

import cn.haiyan.platform.service.CommunityService;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import static cn.haiyan.platform.service.CommunityService.*;

@RestController
public class HomeController {
    private final CommunityService service;
    public HomeController(CommunityService service) { this.service=service; }

    @GetMapping("/api/public/home")
    public Map<String,Object> home() {
        var db=service.store();
        return Map.of(
            "workCount",db.count("SELECT COUNT(*) FROM works WHERE status='PUBLISHED'"),
            "authorCount",db.count("SELECT COUNT(DISTINCT author_id) FROM works WHERE status='PUBLISHED'"),
            "featured",service.decorate(db.list(WORK_SELECT+"WHERE w.status='PUBLISHED' AND EXISTS (SELECT 1 FROM work_tags wt JOIN tags t ON t.id=wt.tag_id WHERE wt.work_id=w.id AND t.name='海燕精选' AND t.kind='TOPIC') ORDER BY w.published_at DESC,w.id DESC LIMIT 3"),false));
    }

    @GetMapping("/api/public/works/random")
    public List<Map<String,Object>> random(@RequestParam(defaultValue="") String exclude) {
        List<Long> ids=new ArrayList<>();
        if(!exclude.isBlank()) {
            if(!exclude.matches("[0-9]+(,[0-9]+){0,2}")||exclude.length()>62)throw error(400,"推荐参数无效");
            try { for(String id:exclude.split(","))ids.add(Long.parseLong(id)); }
            catch(NumberFormatException ex) { throw error(400,"推荐参数无效"); }
        }
        // Keep a full set for small libraries; avoid the previous set when enough works exist.
        boolean omit=!ids.isEmpty()&&service.store().count("SELECT COUNT(*) FROM works WHERE status='PUBLISHED'")>=6;
        String where="WHERE w.status='PUBLISHED'"+(omit?" AND w.id NOT IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")":"");
        return service.decorate(service.store().list(WORK_SELECT+where+" ORDER BY RAND() LIMIT 3",omit?ids.toArray():new Object[0]),false);
    }

    public record FeaturedInput(boolean active) {}
    @PutMapping("/api/admin/works/{id}/featured")
    @Transactional
    public void featured(@PathVariable long id,@RequestBody FeaturedInput input) {
        var db=service.store();
        var work=db.one("SELECT status FROM works WHERE id=? FOR UPDATE",id);
        if(work==null||"DRAFT".equals(work.get("status")))throw error(404,"作品不存在或尚未发布");
        if(input.active()&&!"PUBLISHED".equals(work.get("status")))throw error(409,"只能将公开作品加入精选");
        var tag=db.one("SELECT id FROM tags WHERE name='海燕精选' AND kind='TOPIC'");
        if(tag==null)throw error(409,"精选标签尚未就绪，请刷新后重试");
        if(input.active())db.update("INSERT IGNORE INTO work_tags(work_id,tag_id) VALUES(?,?)",id,tag.get("id"));
        else db.update("DELETE FROM work_tags WHERE work_id=? AND tag_id=?",id,tag.get("id"));
    }
}
