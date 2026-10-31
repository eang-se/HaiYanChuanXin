package cn.haiyan.platform.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.haiyan.platform.service.CommunityService.*;
@Service
public class PenNames {
 private final CommunityStore db;
 public PenNames(CommunityStore db){this.db=db;}
 public Map<String,Object> resolve(long member,Long requested,Map<String,Object> existing){
  Long id=requested;
  if(id==null&&existing!=null&&existing.get("penNameId") instanceof Number n)id=n.longValue();
  if(id==null||id==0)return null;
  if(id<0)throw error(400,"请选择有效笔名");
  var pen=db.one("SELECT p.id,p.name FROM pen_names p JOIN member_pen_names b ON b.pen_name_id=p.id WHERE b.member_id=? AND p.id=?",member,id);
  if(pen==null)throw error(403,"请先在个人中心绑定该笔名");
  return pen;
 }
 public List<Map<String,Object>> list(long member){return db.list("SELECT p.id,p.name,(SELECT COUNT(*) FROM works w WHERE w.pen_name_id=p.id AND w.status='PUBLISHED') AS work_count FROM pen_names p JOIN member_pen_names b ON b.pen_name_id=p.id WHERE b.member_id=? ORDER BY p.id",member);}
 @Transactional public Map<String,Object> bind(long member,String raw){
  String name=bounded(raw,120,"笔名");if(name.isBlank()||name.equals("署名待核"))throw error(400,"请填写可辨识的笔名");checkText(name);
  db.one("SELECT id FROM members WHERE id=? FOR UPDATE",member);
  var existing=db.one("SELECT p.id FROM pen_names p JOIN member_pen_names b ON b.pen_name_id=p.id WHERE b.member_id=? AND p.name=?",member,name);
  if(existing!=null)return existing;
  if(db.count("SELECT COUNT(*) FROM member_pen_names WHERE member_id=?",member)>=30)throw error(400,"每个账号最多绑定 30 个笔名");
  db.update("INSERT INTO pen_names(name) VALUES(?) ON DUPLICATE KEY UPDATE name=name",name);
  var pen=db.one("SELECT id,name FROM pen_names WHERE name=?",name);
  db.update("INSERT IGNORE INTO member_pen_names(member_id,pen_name_id) VALUES(?,?)",member,pen.get("id"));return pen;
 }
 @Transactional public void unbind(long member,long id){
  db.one("SELECT id FROM members WHERE id=? FOR UPDATE",member);
  if(db.count("SELECT COUNT(*) FROM works WHERE author_id=? AND pen_name_id=?",member,id)>0)throw error(400,"仍有稿件使用该笔名，请先修改这些稿件的署名");
  db.update("DELETE FROM member_pen_names WHERE member_id=? AND pen_name_id=?",member,id);
 }
}
