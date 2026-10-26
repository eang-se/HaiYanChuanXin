package cn.haiyan.platform.service;
import java.util.*;
import org.springframework.stereotype.Service;
import static cn.haiyan.platform.service.CommunityService.*;
@Service
public class CollectionTags {
 private final CommunityStore db;public CollectionTags(CommunityStore db){this.db=db;}
 public long tag(long collection,long author,String title){
  db.update("INSERT INTO tags(name,kind,owner_id,collection_id) VALUES(?,'COLLECTION',?,?) ON DUPLICATE KEY UPDATE name=VALUES(name)","合集："+title,author,collection);
  return ((Number)db.one("SELECT id FROM tags WHERE collection_id=?",collection).get("id")).longValue();
 }
 public List<Long> resolve(long author,List<String> names,boolean publish,String genre){
  if(names==null)return List.of();if(names.size()>10)throw error(400,"最多添加 10 个合集标签");
  db.one("SELECT id FROM members WHERE id=? FOR UPDATE",author);
  List<Long> ids=new ArrayList<>();
  for(String raw:names){String name=bounded(raw,110,"合集标签").replaceFirst("^#?合集[：:]","").trim();name=bounded(name,100,"合集名称");if(name.isBlank())continue;
   var c=db.one("SELECT id,auto_created FROM collections WHERE author_id=? AND title=? ORDER BY id LIMIT 1",author,name);
   long cid;if(c==null)cid=db.insert("INSERT INTO collections(author_id,title,kind,published,auto_created) VALUES(?,?,?,?,TRUE)",author,name,"诗歌".equals(genre)?"诗集":"选集",publish);
   else{cid=((Number)c.get("id")).longValue();if(publish&&Boolean.TRUE.equals(c.get("autoCreated")))db.update("UPDATE collections SET published=TRUE WHERE id=?",cid);}
   ids.add(tag(cid,author,name));
  }return ids.stream().distinct().toList();
 }
 public void attach(long work,List<Long> tags){
  var previous=db.list("SELECT wt.tag_id,wt.position FROM work_tags wt JOIN tags t ON t.id=wt.tag_id WHERE wt.work_id=? AND t.kind='COLLECTION'",work);
  db.update("DELETE FROM work_tags WHERE work_id=?",work);
  for(long id:tags){var t=db.one("SELECT kind FROM tags WHERE id=?",id);if("COLLECTION".equals(t.get("kind"))){
    db.one("SELECT id FROM tags WHERE id=? FOR UPDATE",id);
    Object position=previous.stream().filter(r->((Number)r.get("tagId")).longValue()==id).map(r->r.get("position")).filter(Objects::nonNull).findFirst().orElse(null);
    if(position==null)position=db.count("SELECT COALESCE(MAX(position),-1)+1 FROM work_tags WHERE tag_id=?",id);
    db.update("INSERT INTO work_tags(work_id,tag_id,position) VALUES(?,?,?)",work,id,position);
   }else db.update("INSERT INTO work_tags(work_id,tag_id) VALUES(?,?)",work,id);
  }
 }
}
