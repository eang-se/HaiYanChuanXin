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
 public record Input(String name){}
 @PostMapping("/api/member/pen-names") public Map<String,Object> bind(Principal p,@RequestBody Input input){permissions.require(p.getName(),"WRITE");return pens.bind(service.memberId(p.getName()),input.name());}
 @DeleteMapping("/api/member/pen-names/{id}") public void unbind(Principal p,@PathVariable long id){permissions.require(p.getName(),"WRITE");pens.unbind(service.memberId(p.getName()),id);}
 @GetMapping("/api/public/pen-names/{id}") public Map<String,Object> detail(@PathVariable long id){
  var pen=service.store().one("SELECT p.id,p.name,(SELECT COUNT(*) FROM works w WHERE w.pen_name_id=p.id AND w.status='PUBLISHED') AS work_count FROM pen_names p WHERE p.id=?",id);
  if(pen==null||((Number)pen.get("workCount")).longValue()==0)throw error(404,"该笔名暂无公开作品");return pen;
 }
}
