package cn.haiyan.platform.web;

import cn.haiyan.platform.service.CommunityService;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import static cn.haiyan.platform.service.CommunityService.*;

@RestController
@RequestMapping("/api/admin/works")
public class AdminWorksController {
    private final CommunityService service;
    public AdminWorksController(CommunityService service){this.service=service;}
    private static final String FEATURED="EXISTS(SELECT 1 FROM work_tags wt JOIN tags t ON t.id=wt.tag_id WHERE wt.work_id=w.id AND t.name='海燕精选' AND t.kind='TOPIC')";

    @GetMapping
    public Map<String,Object> list(@RequestParam(defaultValue="")String q,
            @RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String genre,
            @RequestParam(defaultValue="false")boolean featured,@RequestParam(defaultValue="updated")String sort,
            @RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="12")int pageSize){
        if(q.length()>100)throw error(400,"搜索词不能超过 100 字");
        if(!status.isEmpty()&&!Set.of("PUBLISHED","WITHDRAWN").contains(status))throw error(400,"文章状态无效");
        if(!Set.of("updated","oldest","newest").contains(sort))throw error(400,"排序方式无效");
        if(page<1||page>10000||pageSize<1||pageSize>100)throw error(400,"分页参数无效");
        var db=service.store();var args=new ArrayList<Object>();String where="WHERE w.status<>'DRAFT'";
        if(!q.isBlank()){where+=" AND (w.title LIKE ? OR w.byline LIKE ? OR pn.name LIKE ? OR m.pen_name LIKE ? OR m.username LIKE ?)";for(int i=0;i<5;i++)args.add("%"+q.trim()+"%");}
        if(!status.isEmpty()){where+=" AND w.status=?";args.add(status);}
        if(!genre.isEmpty()){where+=" AND w.genre=?";args.add(genre);}
        if(featured)where+=" AND "+FEATURED;
        long total=db.count("SELECT COUNT(*) FROM works w JOIN members m ON m.id=w.author_id LEFT JOIN pen_names pn ON pn.id=w.pen_name_id "+where,args.toArray());
        int currentPage=(int)Math.min(page,Math.max(1,(total+pageSize-1)/pageSize));
        String order=switch(sort){case "oldest"->"w.id ASC";case "newest"->"w.id DESC";default->"w.updated_at DESC,w.id DESC";};
        args.add(pageSize);args.add((currentPage-1)*pageSize);
        var items=service.decorate(db.list(WORK_LIST_SELECT+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",args.toArray()),false);
        var summary=db.one("SELECT COUNT(*) AS total,COALESCE(SUM(w.status='PUBLISHED'),0) AS published,COALESCE(SUM(w.status='WITHDRAWN'),0) AS withdrawn,COALESCE(SUM("+FEATURED+"),0) AS featured FROM works w WHERE w.status<>'DRAFT'");
        return Map.of("items",items,"total",total,"page",currentPage,"pageSize",pageSize,"summary",summary);
    }

    @GetMapping("/{id}")
    public Map<String,Object> detail(@PathVariable long id){
        var rows=service.store().list(WORK_SELECT+"WHERE w.id=? AND w.status<>'DRAFT'",id);
        if(rows.isEmpty())throw error(404,"文章不存在");
        return service.decorate(rows,true).getFirst();
    }
    @DeleteMapping("/{id}") public void delete(@PathVariable long id){service.deleteArticle(id);}
    @PostMapping("/{id}/approve") public void approve(@PathVariable long id){service.reviewArticle(id,true);}
}
