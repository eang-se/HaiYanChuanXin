package cn.haiyan.platform.web;
import java.security.Principal;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import cn.haiyan.platform.service.*;
import static cn.haiyan.platform.service.CommunityService.*;
@RestController
public class PenNameController {
 private final CommunityService service;private final PenNames pens;private final AccountPermissions permissions;
 public PenNameController(CommunityService service,PenNames pens,AccountPermissions permissions){this.service=service;this.pens=pens;this.permissions=permissions;}
 @GetMapping("/api/member/pen-names") public List<Map<String,Object>> mine(Principal p){return pens.list(service.memberId(p.getName()));}
 @GetMapping("/api/member/pen-names/availability") public Map<String,Object> availability(Principal p,@RequestParam String name){return pens.availability(service.memberId(p.getName()),name);}
 public record Input(String name){}
 @PostMapping("/api/member/pen-names") public Map<String,Object> bind(Principal p,@RequestBody Input input){permissions.require(p.getName(),"WRITE");return pens.bind(service.memberId(p.getName()),input.name());}
 @DeleteMapping("/api/member/pen-names/{id}") public void unbind(Principal p,@PathVariable long id){permissions.require(p.getName(),"WRITE");pens.unbind(service.memberId(p.getName()),id);}
 @GetMapping("/api/public/pen-names/{id}") public Map<String,Object> detail(@PathVariable long id){
  var pen=service.store().one("SELECT p.id,p.name,(SELECT COUNT(*) FROM works w WHERE w.pen_name_id=p.id AND w.status='PUBLISHED') AS work_count FROM pen_names p WHERE p.id=?",id);
  if(pen==null||((Number)pen.get("workCount")).longValue()==0)throw error(404,"该笔名暂无公开作品");
  var accounts=service.store().list("SELECT m.id,m.pen_name,m.qq_number,m.account_type,m.username,EXISTS (SELECT 1 FROM member_pen_names b WHERE b.member_id=m.id AND b.pen_name_id=?) AS bound FROM members m WHERE EXISTS (SELECT 1 FROM member_pen_names b WHERE b.member_id=m.id AND b.pen_name_id=?) OR EXISTS (SELECT 1 FROM works w WHERE w.author_id=m.id AND w.pen_name_id=? AND w.status='PUBLISHED') ORDER BY bound DESC,m.pen_name,m.id",id,id,id);
  for(var account:accounts){Object bound=account.get("bound");account.put("bound",Boolean.TRUE.equals(bound)||(bound instanceof Number n&&n.intValue()!=0));account.put("avatarUrl",avatarUrl(account.get("id"),account.get("qqNumber"),account.get("accountType"),account.get("username")));account.remove("qqNumber");account.remove("username");}
  pen.put("accounts",accounts);return pen;
 }
}
