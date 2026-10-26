package cn.haiyan.platform.service;
import java.util.*;
import org.springframework.stereotype.Service;
import static cn.haiyan.platform.service.CommunityService.*;
@Service
public class AccountPermissions {
 public static final Map<String,String> CATALOG=Map.of("WRITE","投稿与编辑","COMMENT","发表评论","UPLOAD","上传文件与头像","COLLECTIONS","管理合集");
 private final CommunityStore db;public AccountPermissions(CommunityStore db){this.db=db;}
 public Map<String,Boolean> effective(long id,String role){var result=new TreeMap<String,Boolean>();CATALOG.keySet().forEach(k->result.put(k,true));if(!"ADMIN".equals(role))for(var r:db.list("SELECT permission_code,allowed FROM member_permissions WHERE member_id=?",id))result.put((String)r.get("permissionCode"),Boolean.TRUE.equals(r.get("allowed")));return result;}
 public void require(String username,String code){var m=db.one("SELECT id,role FROM members WHERE username=?",username);if(m==null)throw error(401,"请先登录");if(!Boolean.TRUE.equals(effective(((Number)m.get("id")).longValue(),(String)m.get("role")).get(code)))throw error(403,"当前账号未获此项权限");}
}
