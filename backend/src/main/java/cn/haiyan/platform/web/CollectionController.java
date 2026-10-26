package cn.haiyan.platform.web;

import cn.haiyan.platform.service.*;
import java.security.Principal;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import static cn.haiyan.platform.service.CommunityService.*;

@RestController
public class CollectionController {
 private final CommunityService s;private final CollectionTags tags;private final AccountPermissions permissions;
 public CollectionController(CommunityService s,CollectionTags tags,AccountPermissions permissions){this.s=s;this.tags=tags;this.permissions=permissions;}
 private static final String SELECT="SELECT c.*,m.pen_name AS author_name,(SELECT COUNT(*) FROM tags ct JOIN work_tags cw ON cw.tag_id=ct.id JOIN works w ON w.id=cw.work_id WHERE ct.collection_id=c.id AND w.status='PUBLISHED') AS work_count FROM collections c JOIN members m ON m.id=c.author_id ";
 @GetMapping("/api/public/collections") public Map<String,Object> list(@RequestParam(defaultValue="1")int page,@RequestParam(required=false)Long author){
  String where="WHERE c.published=TRUE"+(author==null?"":" AND c.author_id=?");
  List<Object> args=new ArrayList<>();if(author!=null)args.add(author);
  long total=s.store().count("SELECT COUNT(*) FROM collections c "+where,args.toArray());
  args.add(12);args.add((Math.max(1,Math.min(page,10000))-1)*12);
  return Map.of("items",s.store().list(SELECT+where+" ORDER BY c.updated_at DESC,c.id DESC LIMIT ? OFFSET ?",args.toArray()),"total",total);
 }
 @GetMapping("/api/member/collections") public List<Map<String,Object>> mine(Principal p){return s.store().list(SELECT+"WHERE c.author_id=? ORDER BY c.updated_at DESC,c.id DESC",s.memberId(p.getName()));}
 @GetMapping("/api/public/collections/{id}") public Map<String,Object> detail(@PathVariable long id,Principal p){
  var c=s.store().one(SELECT+"WHERE c.id=?",id);if(c==null)throw error(404,"合集不存在");
  boolean owner=p!=null&&((Number)c.get("authorId")).longValue()==s.memberId(p.getName());
  if(!Boolean.TRUE.equals(c.get("published"))&&!owner)throw error(404,"合集尚未公开");
  c.put("works",s.decorate(s.store().list(WORK_SELECT+"JOIN work_tags cw ON cw.work_id=w.id JOIN tags ct ON ct.id=cw.tag_id WHERE ct.collection_id=?"+(owner?"":" AND w.status='PUBLISHED'")+" ORDER BY cw.position",id),false));
  return c;
 }
 @GetMapping("/api/public/works/{id}/collections") public List<Map<String,Object>> memberships(@PathVariable long id){s.publicWork(id);return s.store().list(SELECT+"WHERE c.published=TRUE AND EXISTS(SELECT 1 FROM work_tags cw JOIN tags ct ON ct.id=cw.tag_id WHERE ct.collection_id=c.id AND cw.work_id=?) ORDER BY c.id",id);}
 public record Input(String title,String description,String kind,boolean published,List<Long> workIds){}
 @PostMapping("/api/member/collections") @Transactional public Map<String,Object> create(Principal p,@RequestBody Input f){return save(null,p,f);}
 @PutMapping("/api/member/collections/{id}") @Transactional public Map<String,Object> update(@PathVariable long id,Principal p,@RequestBody Input f){return save(id,p,f);}
 private Map<String,Object> save(Long id,Principal p,Input f){
  long author=s.memberId(p.getName());permissions.require(p.getName(),"COLLECTIONS");s.store().one("SELECT id FROM members WHERE id=? FOR UPDATE",author);
  if(id!=null&&s.store().count("SELECT COUNT(*) FROM collections WHERE id=? AND author_id=?",id,author)==0)throw error(404,"找不到你的合集");
  String title=bounded(f.title(),100,"合集名称"),description=bounded(f.description(),1000,"简介");
  if(title.isBlank())throw error(400,"请填写合集名称");
  if(s.store().count("SELECT COUNT(*) FROM collections WHERE author_id=? AND title=? AND id<>?",author,title,id==null?0:id)>0)throw error(400,"已有同名合集，请使用其他名称");
  if(!List.of("连载","诗集","选集").contains(f.kind()==null?"":f.kind()))throw error(400,"请选择合集类型");
  List<Long> ids=f.workIds()==null?List.of():f.workIds();
  if(ids.size()>500||new HashSet<>(ids).size()!=ids.size())throw error(400,"合集最多 500 篇，篇目不能重复");
  for(Long wid:ids){if(wid==null)throw error(400,"篇目无效");s.owner(wid,author);}
  if(id==null)id=s.store().insert("INSERT INTO collections(author_id,title,description,kind,published) VALUES(?,?,?,?,?)",author,title,description,f.kind(),f.published());
  else s.store().update("UPDATE collections SET title=?,description=?,kind=?,published=?,auto_created=FALSE,updated_at=CURRENT_TIMESTAMP WHERE id=?",title,description,f.kind(),f.published(),id);
  long tagId=tags.tag(id,author,title);s.store().one("SELECT id FROM tags WHERE id=? FOR UPDATE",tagId);s.store().update("DELETE FROM work_tags WHERE tag_id=?",tagId);
  for(int i=0;i<ids.size();i++)s.store().update("INSERT INTO work_tags(work_id,tag_id,position) VALUES(?,?,?)",ids.get(i),tagId,i);
  return Map.of("id",id);
 }
}
