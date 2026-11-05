package cn.haiyan.platform.web;

import cn.haiyan.platform.service.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import static cn.haiyan.platform.service.CommunityService.*;

@RestController
public class SearchController {
 private final CommunityService service;
 public SearchController(CommunityService service){this.service=service;}
 @GetMapping("/api/public/search")
 public Map<String,Object> search(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="works") String type,@RequestParam(defaultValue="1") int page){
  String query=q.trim();if(query.codePointCount(0,query.length())>100)throw error(400,"搜索词最多 100 个字符");
  if(!Set.of("works","authors","tags").contains(type))throw error(400,"搜索类型无效");
  if(query.isEmpty())return Map.of("items",List.of(),"total",0);
  String term="%"+query.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
  String match=switch(type){
   case "authors" -> "(w.byline LIKE ? ESCAPE '!' OR m.pen_name LIKE ? ESCAPE '!' OR pn.name LIKE ? ESCAPE '!' OR EXISTS(SELECT 1 FROM member_pen_names b JOIN pen_names p ON p.id=b.pen_name_id WHERE b.member_id=w.author_id AND p.name LIKE ? ESCAPE '!'))";
   case "tags" -> "EXISTS(SELECT 1 FROM work_tags wt JOIN tags t ON t.id=wt.tag_id WHERE wt.work_id=w.id AND t.kind='TOPIC' AND (t.name LIKE ? ESCAPE '!' OR t.description LIKE ? ESCAPE '!'))";
   default -> "(w.title LIKE ? ESCAPE '!' OR w.plain_text LIKE ? ESCAPE '!')";
  };
  List<Object> args=new ArrayList<>();for(int i=0;i<(type.equals("authors")?4:2);i++)args.add(term);
  String where=" WHERE w.status='PUBLISHED' AND "+match;
  var db=service.store();long total=db.count("SELECT COUNT(*) FROM works w LEFT JOIN members m ON m.id=w.author_id LEFT JOIN pen_names pn ON pn.id=w.pen_name_id"+where,args.toArray());
  args.add(12);args.add((Math.min(10000,Math.max(1,page))-1)*12);
  var items=service.decorate(db.list(WORK_LIST_SELECT+where+" ORDER BY w.published_at DESC,w.id DESC LIMIT ? OFFSET ?",args.toArray()),false);
  return Map.of("items",items,"total",total);
 }
}
