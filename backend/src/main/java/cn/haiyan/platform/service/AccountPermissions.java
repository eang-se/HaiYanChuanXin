package cn.haiyan.platform.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static cn.haiyan.platform.service.CommunityService.*;
@Service
public class AccountPermissions {
 public static final Map<String,String> CATALOG=Map.of("WRITE","投稿与编辑","COMMENT","发表评论","UPLOAD","上传文件","COLLECTIONS","管理合集","REACT","点赞与收藏");
 private final CommunityStore db;public AccountPermissions(CommunityStore db){this.db=db;}
 public Map<String,Boolean> effective(long id,String role){
  var result=new TreeMap<String,Boolean>();boolean allowedByDefault=!"RESTRICTED".equals(role);
  CATALOG.keySet().forEach(k->result.put(k,allowedByDefault));
  if(!List.of("ADMIN","SUPER_ADMIN").contains(role))for(var r:db.list("SELECT permission_code,allowed FROM member_permissions WHERE member_id=?",id))if(CATALOG.containsKey((String)r.get("permissionCode")))result.put((String)r.get("permissionCode"),Boolean.TRUE.equals(r.get("allowed")));
  return result;
 }
 public Map<String,Boolean> currentEffective(String username){
  var m=db.one("SELECT id,role,account_type FROM members WHERE username=?",username);if(m==null)throw error(401,"请先登录");
  var result=effective(((Number)m.get("id")).longValue(),(String)m.get("role"));
  if("PUBLIC".equals(m.get("accountType"))){
   var attributes=RequestContextHolder.getRequestAttributes();
   var session=attributes instanceof ServletRequestAttributes a?a.getRequest().getSession(false):null;
   Object actorName=session==null?null:session.getAttribute("actorUsername");
   if(!(actorName instanceof String actorUsername))throw error(403,"公用身份需要个人账号授权");
   var actor=db.one("SELECT id,role,account_type FROM members WHERE username=?",actorUsername);
   if(actor==null||!"PERSONAL".equals(actor.get("accountType")))throw error(403,"个人账号不可用");
   var actorPermissions=effective(((Number)actor.get("id")).longValue(),(String)actor.get("role"));
   result.replaceAll((key,value)->value&&Boolean.TRUE.equals(actorPermissions.get(key)));
  }
  return result;
 }
 public void require(String username,String code){if(!Boolean.TRUE.equals(currentEffective(username).get(code)))throw error(403,"当前账号未获此项权限");}
}
