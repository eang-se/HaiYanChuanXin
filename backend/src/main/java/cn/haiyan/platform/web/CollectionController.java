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
  c.put("works",s.decorate(s.store().list(WORK_LIST_SELECT+"WHERE w.collection_id=?"+(owner?"":" AND w.status='PUBLISHED'")+" ORDER BY w.collection_position,w.id",id),false,owner?s.memberId(p.getName()):null));return c;
 }
 @GetMapping("/api/public/works/{id}/collections") public List<Map<String,Object>> memberships(@PathVariable long id){s.publicWork(id);return s.store().list(SELECT+"WHERE c.published=TRUE AND EXISTS(SELECT 1 FROM works w WHERE w.collection_id=c.id AND w.id=?)",id);}
 public record Input(String title,String description,String kind,boolean published,List<Long> workIds){}
 @PostMapping("/api/member/collections") @Transactional public Map<String,Object> create(Principal p,@RequestBody Input f){return save(null,p,f);}
 @PutMapping("/api/member/collections/{id}") @Transactional public Map<String,Object> update(@PathVariable long id,Principal p,@RequestBody Input f){return save(id,p,f);}
 @DeleteMapping("/api/member/collections/{id}") @Transactional public void delete(@PathVariable long id,Principal p){
  long author=s.memberId(p.getName());permissions.require(p.getName(),"COLLECTIONS");s.store().one("SELECT id FROM members WHERE id=? FOR UPDATE",author);
  if(s.store().one("SELECT id FROM collections WHERE id=? AND author_id=? FOR UPDATE",id,author)==null)throw error(404,"找不到你的合集");
  // Include draft-only selections so open editors and pending reviews cannot retain a deleted collection.
  var affected=s.store().list("SELECT w.id FROM works w LEFT JOIN work_drafts d ON d.work_id=w.id WHERE w.author_id=? AND (w.collection_id=? OR JSON_UNQUOTE(JSON_EXTRACT(d.payload,'$.collectionId'))=?) FOR UPDATE",author,id,String.valueOf(id));
  for(var row:affected){long wid=((Number)row.get("id")).longValue();
   s.store().update("UPDATE works SET edit_revision=edit_revision+1,updated_at=updated_at WHERE id=? AND author_id=?",wid,author);
   s.store().update("UPDATE work_drafts SET payload=JSON_SET(payload,'$.collectionId',0),revision=revision+1 WHERE work_id=? AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.collectionId'))=?",wid,String.valueOf(id));
   s.store().update("UPDATE article_reviews SET status='SUPERSEDED',decided_at=CURRENT_TIMESTAMP WHERE work_id=? AND status='PENDING'",wid);
  }
  s.store().update("UPDATE works SET collection_id=NULL,collection_position=NULL,updated_at=updated_at WHERE collection_id=? AND author_id=?",id,author);
  s.store().update("DELETE FROM collection_works WHERE collection_id=?",id);
  s.store().update("DELETE wt FROM work_tags wt JOIN tags t ON t.id=wt.tag_id WHERE t.collection_id=? AND t.kind='COLLECTION'",id);
  s.store().update("DELETE FROM tags WHERE collection_id=? AND kind='COLLECTION'",id);
  s.store().update("DELETE FROM collections WHERE id=? AND author_id=?",id,author);
 }

 private Map<String,Object> save(Long id,Principal p,Input f){
  long author=s.memberId(p.getName());permissions.require(p.getName(),"COLLECTIONS");s.store().one("SELECT id FROM members WHERE id=? FOR UPDATE",author);
  if(id!=null&&s.store().count("SELECT COUNT(*) FROM collections WHERE id=? AND author_id=?",id,author)==0)throw error(404,"找不到你的合集");
  String title=bounded(f.title(),100,"合集名称"),description=bounded(f.description(),1000,"简介");if(title.isBlank())throw error(400,"请填写合集名称");
  if(s.store().count("SELECT COUNT(*) FROM collections WHERE author_id=? AND title=? AND id<>?",author,title,id==null?0:id)>0)throw error(400,"已有同名合集，请使用其他名称");
  if(!List.of("连载","诗集","选集").contains(f.kind()==null?"":f.kind()))throw error(400,"请选择合集类型");
  List<Long> ids=f.workIds()==null?List.of():f.workIds();if(ids.size()>500||new HashSet<>(ids).size()!=ids.size())throw error(400,"合集最多 500 篇，篇目不能重复");
  Map<Long,Long> oldMembership=new LinkedHashMap<>();
  if(id!=null)for(var row:s.store().list("SELECT id FROM works WHERE collection_id=? AND author_id=?",id,author))oldMembership.put(((Number)row.get("id")).longValue(),id);
  for(Long wid:ids){
   if(wid==null)throw error(400,"篇目无效");
   var work=s.store().one("SELECT id,collection_id FROM works WHERE id=? AND author_id=? FOR UPDATE",wid,author);
   if(work==null)throw error(404,"找不到你的稿件");
   if(work.get("collectionId") instanceof Number n&&(id==null||n.longValue()!=id))throw error(400,"一篇文章只能绑定一部合集，请先从原合集中移出");
   oldMembership.putIfAbsent(wid,work.get("collectionId") instanceof Number n?n.longValue():0L);
  }
  if(id==null)id=s.store().insert("INSERT INTO collections(author_id,title,description,kind,published) VALUES(?,?,?,?,?)",author,title,description,f.kind(),f.published());
  else s.store().update("UPDATE collections SET title=?,description=?,kind=?,published=?,auto_created=FALSE,updated_at=CURRENT_TIMESTAMP WHERE id=?",title,description,f.kind(),f.published(),id);
  s.store().update("UPDATE works SET collection_id=NULL,collection_position=NULL WHERE collection_id=? AND author_id=?",id,author);
  for(int i=0;i<ids.size();i++)s.store().update("UPDATE works SET collection_id=?,collection_position=? WHERE id=? AND author_id=?",id,i,ids.get(i),author);
  Set<Long> selected=new HashSet<>(ids);
  for(var entry:oldMembership.entrySet()){
   long wid=entry.getKey(),before=entry.getValue(),after=selected.contains(wid)?id:0;
   s.store().update("UPDATE works SET edit_revision=edit_revision+1,updated_at=updated_at WHERE id=?",wid);
   // Preserve an intentional different draft selection; update drafts still matching the former public association.
   if(before!=after)s.store().update("UPDATE work_drafts SET payload=JSON_SET(payload,'$.collectionId',?),revision=revision+1 WHERE work_id=? AND COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(payload,'$.collectionId')),'null'),'0')=?",after,wid,String.valueOf(before));
  }
  return Map.of("id",id);
 }
}
