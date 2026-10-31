package cn.haiyan.platform.web;
import cn.haiyan.platform.service.*;
import java.security.Principal;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import static cn.haiyan.platform.service.CommunityService.*;
@RestController
public class CollectionController {
 private final CommunityService s;private final AccountPermissions permissions;
 public CollectionController(CommunityService s,AccountPermissions permissions){this.s=s;this.permissions=permissions;}
 private static final String SELECT="SELECT c.*,m.pen_name AS author_name,(SELECT COUNT(*) FROM works w WHERE w.collection_id=c.id AND w.status='PUBLISHED') AS work_count FROM collections c JOIN members m ON m.id=c.author_id ";
 @GetMapping("/api/public/collections") public Map<String,Object> list(@RequestParam(defaultValue="1")int page,@RequestParam(required=false)Long author){
  String where="WHERE c.published=TRUE"+(author==null?"":" AND c.author_id=?");List<Object> args=new ArrayList<>();if(author!=null)args.add(author);
  long total=s.store().count("SELECT COUNT(*) FROM collections c "+where,args.toArray());args.add(12);args.add((Math.max(1,Math.min(page,10000))-1)*12);
  return Map.of("items",s.store().list(SELECT+where+" ORDER BY c.updated_at DESC,c.id DESC LIMIT ? OFFSET ?",args.toArray()),"total",total);
 }
 @GetMapping("/api/member/collections") public List<Map<String,Object>> mine(Principal p){return s.store().list(SELECT+"WHERE c.author_id=? ORDER BY c.updated_at DESC,c.id DESC",s.memberId(p.getName()));}
 @GetMapping("/api/public/collections/{id}") public Map<String,Object> detail(@PathVariable long id,Principal p){
  var c=s.store().one(SELECT+"WHERE c.id=?",id);if(c==null)throw error(404,"合集不存在");
  boolean owner=p!=null&&((Number)c.get("authorId")).longValue()==s.memberId(p.getName());
  if(!Boolean.TRUE.equals(c.get("published"))&&!owner)throw error(404,"合集尚未公开");
  c.put("works",s.decorate(s.store().list(WORK_SELECT+"WHERE w.collection_id=?"+(owner?"":" AND w.status='PUBLISHED'")+" ORDER BY w.collection_position,w.id",id),false,owner?s.memberId(p.getName()):null));return c;
 }
 @GetMapping("/api/public/works/{id}/collections") public List<Map<String,Object>> memberships(@PathVariable long id){s.publicWork(id);return s.store().list(SELECT+"WHERE c.published=TRUE AND EXISTS(SELECT 1 FROM works w WHERE w.collection_id=c.id AND w.id=?)",id);}
 public record Input(String title,String description,String kind,boolean published,List<Long> workIds){}
 @PostMapping("/api/member/collections") @Transactional public Map<String,Object> create(Principal p,@RequestBody Input f){return save(null,p,f);}
 @PutMapping("/api/member/collections/{id}") @Transactional public Map<String,Object> update(@PathVariable long id,Principal p,@RequestBody Input f){return save(id,p,f);}
 private Map<String,Object> save(Long id,Principal p,Input f){
  long author=s.memberId(p.getName());permissions.require(p.getName(),"COLLECTIONS");s.store().one("SELECT id FROM members WHERE id=? FOR UPDATE",author);
  if(id!=null&&s.store().count("SELECT COUNT(*) FROM collections WHERE id=? AND author_id=?",id,author)==0)throw error(404,"找不到你的合集");
  String title=bounded(f.title(),100,"合集名称"),description=bounded(f.description(),1000,"简介");if(title.isBlank())throw error(400,"请填写合集名称");
  if(s.store().count("SELECT COUNT(*) FROM collections WHERE author_id=? AND title=? AND id<>?",author,title,id==null?0:id)>0)throw error(400,"已有同名合集，请使用其他名称");
  if(!List.of("连载","诗集","选集").contains(f.kind()==null?"":f.kind()))throw error(400,"请选择合集类型");
  List<Long> ids=f.workIds()==null?List.of():f.workIds();if(ids.size()>500||new HashSet<>(ids).size()!=ids.size())throw error(400,"合集最多 500 篇，篇目不能重复");
  for(Long wid:ids){
   if(wid==null)throw error(400,"篇目无效");
   var work=s.store().one("SELECT id,collection_id FROM works WHERE id=? AND author_id=? FOR UPDATE",wid,author);
   if(work==null)throw error(404,"找不到你的稿件");
   if(work.get("collectionId") instanceof Number n&&(id==null||n.longValue()!=id))throw error(400,"一篇文章只能绑定一部合集，请先从原合集中移出");
  }
  if(id==null)id=s.store().insert("INSERT INTO collections(author_id,title,description,kind,published) VALUES(?,?,?,?,?)",author,title,description,f.kind(),f.published());
  else s.store().update("UPDATE collections SET title=?,description=?,kind=?,published=?,auto_created=FALSE,updated_at=CURRENT_TIMESTAMP WHERE id=?",title,description,f.kind(),f.published(),id);
  s.store().update("UPDATE works SET collection_id=NULL,collection_position=NULL WHERE collection_id=? AND author_id=?",id,author);
  for(int i=0;i<ids.size();i++)s.store().update("UPDATE works SET collection_id=?,collection_position=? WHERE id=? AND author_id=?",id,i,ids.get(i),author);
  return Map.of("id",id);
 }
}
