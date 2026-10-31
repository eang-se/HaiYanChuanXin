package cn.haiyan.platform.service;
import java.util.*;
import org.springframework.stereotype.Service;
import static cn.haiyan.platform.service.CommunityService.*;
@Service
public class WorkCollections {
 private final CommunityStore db;
 public WorkCollections(CommunityStore db){this.db=db;}
 public void assign(long work,long author,Long requested){
  if(requested==null)return;
  if(requested<0)throw error(400,"请选择有效合集");
  var current=db.one("SELECT collection_id FROM works WHERE id=? AND author_id=? FOR UPDATE",work,author);
  if(current==null)throw error(404,"找不到你的稿件");
  Long old=current.get("collectionId") instanceof Number n?n.longValue():null;
  if(requested==0){db.update("UPDATE works SET collection_id=NULL,collection_position=NULL WHERE id=?",work);return;}
  var c=db.one("SELECT id FROM collections WHERE id=? AND author_id=? FOR UPDATE",requested,author);
  if(c==null)throw error(403,"只能选择自己管理的合集");
  if(Objects.equals(old,requested))return;
  if(db.count("SELECT COUNT(*) FROM works WHERE collection_id=?",requested)>=500)throw error(400,"合集最多收录 500 篇文章");
  long position=db.count("SELECT COALESCE(MAX(collection_position),-1)+1 FROM works WHERE collection_id=?",requested);
  db.update("UPDATE works SET collection_id=?,collection_position=? WHERE id=?",requested,position,work);
 }
}
