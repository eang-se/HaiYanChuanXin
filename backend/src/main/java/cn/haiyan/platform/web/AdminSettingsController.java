package cn.haiyan.platform.web;
import cn.haiyan.platform.service.*;
import java.security.Principal;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import static cn.haiyan.platform.service.CommunityService.*;
@RestController
@RequestMapping("/api/admin")
public class AdminSettingsController {
 private final CommunityStore db;private final RegistrationPolicy policy;private final AccountPermissions permissions;
 public AdminSettingsController(CommunityStore db,RegistrationPolicy policy,AccountPermissions permissions){this.db=db;this.policy=policy;this.permissions=permissions;}
 @GetMapping("/settings") public Map<String,Object> settings(){return Map.of("siteName",policy.setting("site_name","海燕传信"),"siteDescription",policy.setting("site_description",""),"registrationEnabled",policy.enabled(),"registrationMode",policy.testMode()?"test":"live","permissionCatalog",AccountPermissions.CATALOG);}
 public record Settings(String siteName,String siteDescription,boolean registrationEnabled,String registrationMode){}
 @PutMapping("/settings") @Transactional public void settings(@RequestBody Settings f){
  String name=bounded(f.siteName(),80,"站点名称"),description=bounded(f.siteDescription(),300,"站点介绍");if(name.isBlank())throw error(400,"站点名称不能为空");if(!List.of("test","live").contains(f.registrationMode()==null?"":f.registrationMode()))throw error(400,"注册模式无效");
  for(var e:Map.of("site_name",name,"site_description",description,"registration_enabled",String.valueOf(f.registrationEnabled()),"registration_mode",f.registrationMode()).entrySet())db.update("INSERT INTO platform_metadata(setting_key,setting_value) VALUES(?,?) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value)",e.getKey(),e.getValue());
 }
 @GetMapping("/accounts") public List<Map<String,Object>> accounts(){return db.list("SELECT id,username,pen_name,role,qq_number,created_at FROM members ORDER BY id DESC");}
 @GetMapping("/accounts/{id}/permissions") public Map<String,Object> account(@PathVariable long id){var m=db.one("SELECT id,username,role FROM members WHERE id=?",id);if(m==null)throw error(404,"账号不存在");m.put("permissions",permissions.effective(id,(String)m.get("role")));return m;}
 public record PermissionInput(String role,Map<String,Boolean> permissions){}
 @PutMapping("/accounts/{id}/permissions") @Transactional public void update(@PathVariable long id,@RequestBody PermissionInput f,Principal p){
  if(!List.of("ADMIN","MEMBER").contains(f.role()==null?"":f.role()))throw error(400,"角色无效");
  if(f.permissions()==null||!AccountPermissions.CATALOG.keySet().containsAll(f.permissions().keySet())||f.permissions().values().stream().anyMatch(Objects::isNull))throw error(400,"权限配置无效");
  var admins=db.list("SELECT id FROM members WHERE role='ADMIN' FOR UPDATE");var m=db.one("SELECT id,role FROM members WHERE id=? FOR UPDATE",id);if(m==null)throw error(404,"账号不存在");
  if("ADMIN".equals(m.get("role"))&&!"ADMIN".equals(f.role())&&admins.size()<2)throw error(400,"必须至少保留一位管理员");
  db.update("UPDATE members SET role=? WHERE id=?",f.role(),id);db.update("DELETE FROM member_permissions WHERE member_id=?",id);
  for(var e:f.permissions().entrySet())db.update("INSERT INTO member_permissions(member_id,permission_code,allowed) VALUES(?,?,?)",id,e.getKey(),e.getValue());
 }
 @GetMapping("/registration/qq-allowlist") public List<Map<String,Object>> qqs(){return db.list("SELECT qq_number,active FROM qq_allowlist ORDER BY qq_number");}
 public record QqImport(List<String> qqNumbers){}
 @PostMapping("/registration/qq-allowlist") @Transactional public Map<String,Object> importQqs(@RequestBody QqImport f){
  if(f.qqNumbers()==null||f.qqNumbers().size()>1000)throw error(400,"每次最多导入 1000 个 QQ 号");
  var values=f.qqNumbers().stream().map(RegistrationPolicy::qq).distinct().toList();for(String qq:values)db.update("INSERT INTO qq_allowlist(qq_number,active) VALUES(?,TRUE) ON DUPLICATE KEY UPDATE active=TRUE",qq);return Map.of("count",values.size());
 }
 @DeleteMapping("/registration/qq-allowlist/{qq}") public void disable(@PathVariable String qq){db.update("UPDATE qq_allowlist SET active=FALSE WHERE qq_number=?",RegistrationPolicy.qq(qq));}
}
