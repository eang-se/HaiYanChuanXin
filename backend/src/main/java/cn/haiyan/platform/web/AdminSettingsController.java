package cn.haiyan.platform.web;
import cn.haiyan.platform.service.*;
import java.security.Principal;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static cn.haiyan.platform.service.CommunityService.*;
@RestController
@RequestMapping("/api/admin")
public class AdminSettingsController {
 private final CommunityStore db;private final RegistrationPolicy policy;private final AccountPermissions permissions;private final MarkdownQqDirectory directory;
 public AdminSettingsController(CommunityStore db,RegistrationPolicy policy,AccountPermissions permissions,MarkdownQqDirectory directory){this.db=db;this.policy=policy;this.permissions=permissions;this.directory=directory;}
 private Map<String,Object> actor(Principal principal){
  var attrs=RequestContextHolder.getRequestAttributes();var session=attrs instanceof ServletRequestAttributes a?a.getRequest().getSession(false):null;
  Object stored=session==null?null:session.getAttribute("actorUsername");String username=stored instanceof String s?s:principal.getName();
  var actor=db.one("SELECT id,role,account_type FROM members WHERE username=?",username);
  if(actor==null||!"PERSONAL".equals(actor.get("accountType"))||!List.of("ADMIN","SUPER_ADMIN").contains(actor.get("role")))throw error(403,"需要管理员权限");return actor;
 }
 @GetMapping("/settings") public Map<String,Object> settings(){return Map.of("siteName",policy.setting("site_name","海燕传信"),"siteDescription",policy.setting("site_description",""),"registrationEnabled",policy.enabled(),"registrationMode",policy.testMode()?"test":"live","permissionCatalog",AccountPermissions.CATALOG);}
 public record Settings(String siteName,String siteDescription,boolean registrationEnabled,String registrationMode){}
 @PutMapping("/settings") @Transactional public void settings(@RequestBody Settings f){
  String name=bounded(f.siteName(),80,"站点名称"),description=bounded(f.siteDescription(),300,"站点介绍");if(name.isBlank())throw error(400,"站点名称不能为空");if(!List.of("test","live").contains(f.registrationMode()==null?"":f.registrationMode()))throw error(400,"注册模式无效");
  var authentication=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
  boolean admin=authentication!=null&&authentication.getAuthorities().stream().anyMatch(a->"ROLE_ADMIN".equals(a.getAuthority()));
  if(!admin&&(f.registrationEnabled()!=policy.enabled()||!f.registrationMode().equals(policy.testMode()?"test":"live")))throw error(403,"注册设置需要管理员权限");
  for(var e:Map.of("site_name",name,"site_description",description,"registration_enabled",String.valueOf(f.registrationEnabled()),"registration_mode",f.registrationMode()).entrySet())db.update("INSERT INTO platform_metadata(setting_key,setting_value) VALUES(?,?) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value)",e.getKey(),e.getValue());
 }
 @GetMapping("/accounts") public List<Map<String,Object>> accounts(){return db.list("SELECT id,username,pen_name,role,account_type,qq_number,created_at FROM members ORDER BY id DESC");}
 @GetMapping("/accounts/{id}/permissions") public Map<String,Object> account(@PathVariable long id){var m=db.one("SELECT id,username,role,account_type FROM members WHERE id=?",id);if(m==null)throw error(404,"账号不存在");m.put("permissions",permissions.effective(id,(String)m.get("role")));return m;}
 public record PermissionInput(String role,Map<String,Boolean> permissions){}
 @PutMapping("/accounts/{id}/permissions") @Transactional public void update(@PathVariable long id,@RequestBody PermissionInput f,Principal p){
  if(!List.of("SUPER_ADMIN","ADMIN","MEMBER","RESTRICTED").contains(f.role()==null?"":f.role()))throw error(400,"角色无效");
  if(f.permissions()==null||!AccountPermissions.CATALOG.keySet().containsAll(f.permissions().keySet())||f.permissions().values().stream().anyMatch(Objects::isNull))throw error(400,"权限配置无效");
  var actor=actor(p);var supers=db.list("SELECT id FROM members WHERE role='SUPER_ADMIN' AND account_type='PERSONAL' FOR UPDATE");
  var m=db.one("SELECT id,role,account_type FROM members WHERE id=? FOR UPDATE",id);if(m==null)throw error(404,"账号不存在");if(!"PERSONAL".equals(m.get("accountType")))throw error(400,"公用身份不能设置个人角色");
  if(!"SUPER_ADMIN".equals(actor.get("role"))&&(List.of("ADMIN","SUPER_ADMIN").contains(m.get("role"))||List.of("ADMIN","SUPER_ADMIN").contains(f.role())))throw error(403,"只有超级管理员可以管理管理员角色");
  if("SUPER_ADMIN".equals(m.get("role"))&&!"SUPER_ADMIN".equals(f.role())&&supers.size()<2)throw error(400,"必须至少保留一位超级管理员");
  db.update("UPDATE members SET role=? WHERE id=?",f.role(),id);db.update("DELETE FROM member_permissions WHERE member_id=?",id);
  for(var e:f.permissions().entrySet())db.update("INSERT INTO member_permissions(member_id,permission_code,allowed) VALUES(?,?,?)",id,e.getKey(),e.getValue());
 }
 @GetMapping("/public-accounts") public List<Map<String,Object>> publicAccounts(){
  var accounts=db.list("SELECT id,username,pen_name,account_type FROM members WHERE account_type='PUBLIC' ORDER BY id");
  for(var account:accounts){long id=((Number)account.get("id")).longValue();account.put("memberIds",db.list("SELECT member_id FROM public_account_members WHERE public_member_id=? ORDER BY member_id",id).stream().map(row->((Number)row.get("memberId")).longValue()).toList());}
  return accounts;
 }
 public record PublicMembers(List<Long> memberIds){}
 @PutMapping("/public-accounts/{id}/members") @Transactional public void publicMembers(@PathVariable long id,@RequestBody PublicMembers f,Principal p){
  if(!"SUPER_ADMIN".equals(actor(p).get("role")))throw error(403,"只有超级管理员可以配置公用账号名单");
  if(f.memberIds()==null||f.memberIds().size()>500||f.memberIds().stream().anyMatch(v->v==null||v<1))throw error(400,"名单无效");
  var unique=new LinkedHashSet<>(f.memberIds());if(unique.size()!=f.memberIds().size())throw error(400,"名单存在重复账号");
  var account=db.one("SELECT id FROM members WHERE id=? AND account_type='PUBLIC' FOR UPDATE",id);if(account==null)throw error(404,"公用账号不存在");
  for(long memberId:unique)if(db.count("SELECT COUNT(*) FROM members WHERE id=? AND account_type='PERSONAL'",memberId)==0)throw error(400,"名单只能包含个人账号");
  db.update("DELETE FROM public_account_members WHERE public_member_id=?",id);
  for(long memberId:unique)db.update("INSERT INTO public_account_members(public_member_id,member_id) VALUES(?,?)",id,memberId);
 }
 @GetMapping("/registration/qq-allowlist") public List<Map<String,Object>> qqs(){
  var disabled=new HashSet<String>();for(var row:db.list("SELECT qq_number FROM qq_allowlist WHERE active=FALSE"))disabled.add((String)row.get("qqNumber"));
  var bound=new HashSet<String>();for(var row:db.list("SELECT qq_number FROM qq_invitation_bindings"))bound.add((String)row.get("qqNumber"));
  var result=new ArrayList<Map<String,Object>>();directory.groups().forEach((group,entries)->entries.forEach((qq,name)->result.add(Map.of("groupId",group,"qqNumber",qq,"name",name,"active",!disabled.contains(qq),"bound",bound.contains(qq)))));return result;
 }
 @GetMapping("/registration/qq-groups") public List<Map<String,Object>> groups(){return directory.groups().entrySet().stream().map(entry->Map.<String,Object>of("groupId",entry.getKey(),"count",entry.getValue().size())).toList();}
 public record QqImport(String groupId,List<String> qqNumbers){}
 @PostMapping("/registration/qq-allowlist") @Transactional public Map<String,Object> importQqs(@RequestBody QqImport f){
  if(f.qqNumbers()==null||f.qqNumbers().size()>1000)throw error(400,"每次最多导入 1000 个 QQ 号");
  var values=f.qqNumbers().stream().map(RegistrationPolicy::qq).distinct().toList();var roster=directory.groups().get(f.groupId());if(roster==null||values.stream().anyMatch(qq->!roster.containsKey(qq)))throw error(400,"请先将 QQ 号和名称添加到对应群的名单文件");for(String qq:values)db.update("INSERT INTO qq_allowlist(qq_number,active) VALUES(?,TRUE) ON DUPLICATE KEY UPDATE active=TRUE",qq);return Map.of("count",values.size());
 }
 @DeleteMapping("/registration/qq-allowlist/{groupId}/{qq}") public void disable(@PathVariable String groupId,@PathVariable String qq){String value=RegistrationPolicy.qq(qq);var group=directory.groups().get(groupId);if(group==null||!group.containsKey(value))throw error(404,"QQ 号不在该群名单中");db.update("INSERT INTO qq_allowlist(qq_number,active) VALUES(?,FALSE) ON DUPLICATE KEY UPDATE active=FALSE",value);}
}
