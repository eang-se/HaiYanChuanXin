package cn.haiyan.platform.web;
import cn.haiyan.platform.service.*;
import java.security.Principal;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import static cn.haiyan.platform.service.CommunityService.*;

@RestController
public class CommunityController {
    private final CommunityService s;private final AccountPermissions permissions;
    public CommunityController(CommunityService s,AccountPermissions permissions){this.s=s;this.permissions=permissions;}
    @GetMapping("/api/public/works") public Map<String,Object> works(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String genre,@RequestParam(required=false)Long tag,@RequestParam(required=false)Long author,@RequestParam(defaultValue="latest")String sort,@RequestParam(defaultValue="1")int page){
        String where="WHERE w.status='PUBLISHED'";List<Object> args=new ArrayList<>();
        if(!q.isBlank()){if(q.length()>100)throw error(400,"搜索词过长");where+=" AND (w.title LIKE ? OR w.plain_text LIKE ? OR m.pen_name LIKE ?)";for(int i=0;i<3;i++)args.add("%"+q+"%");}
        if(!genre.isBlank()){where+=" AND w.genre=?";args.add(genre);}
        if(tag!=null){where+=" AND EXISTS(SELECT 1 FROM work_tags wt WHERE wt.work_id=w.id AND wt.tag_id=?)";args.add(tag);}
        if(author!=null){where+=" AND w.author_id=?";args.add(author);}
        long total=s.store().count("SELECT COUNT(*) FROM works w JOIN members m ON m.id=w.author_id "+where,args.toArray());
        args.add(12);args.add((Math.min(10000,Math.max(1,page))-1)*12);
        String order="popular".equals(sort)?"likes DESC,w.published_at DESC":"w.published_at DESC,w.id DESC";
        return Map.of("items",s.decorate(s.store().list(WORK_SELECT+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",args.toArray()),false),"total",total);
    }
    @GetMapping("/api/public/works/{id}") public Map<String,Object> work(@PathVariable long id,Principal p){return s.work(id,p==null?null:p.getName());}
    @GetMapping("/api/public/tags") public List<Map<String,Object>> tags(){return s.store().list("SELECT t.*,(SELECT COUNT(*) FROM work_tags wt JOIN works w ON wt.work_id=w.id WHERE wt.tag_id=t.id AND w.status='PUBLISHED') AS work_count FROM tags t WHERE t.kind='TOPIC' ORDER BY t.is_activity DESC,t.id");}
    @GetMapping("/api/public/authors/{id}")public Map<String,Object> author(@PathVariable long id){var a=s.store().one("SELECT id,pen_name,bio,avatar_url,signature,created_at FROM members WHERE id=?",id);if(a==null)throw error(404,"作者不存在");return a;}
    @GetMapping("/api/member/works")public List<Map<String,Object>> mine(Principal p){return s.decorate(s.store().list(WORK_SELECT+"WHERE author_id=? ORDER BY w.updated_at DESC",s.memberId(p.getName())),false,s.memberId(p.getName()));}
    @GetMapping("/api/member/bookmarks")public List<Map<String,Object>> bookmarks(Principal p){return s.decorate(s.store().list(WORK_SELECT+"WHERE w.status='PUBLISHED' AND EXISTS(SELECT 1 FROM reactions r WHERE r.work_id=w.id AND r.member_id=? AND r.kind='BOOKMARK') ORDER BY w.published_at DESC",s.memberId(p.getName())),false,s.memberId(p.getName()));}
    @PostMapping("/api/member/works")public Map<String,Object> create(Principal p,@RequestBody WorkInput f){return s.save(null,p.getName(),f);}
    @PutMapping("/api/member/works/{id}")public Map<String,Object> update(@PathVariable long id,Principal p,@RequestBody WorkInput f){return s.save(id,p.getName(),f);}
    @PostMapping("/api/member/works/{id}/withdraw")public void withdraw(@PathVariable long id,Principal p){s.owner(id,s.memberId(p.getName()));s.store().update("UPDATE works SET status='WITHDRAWN' WHERE id=?",id);}
    public record Reaction(String kind,boolean active){}
    @PutMapping("/api/member/works/{id}/reaction")public void react(@PathVariable long id,Principal p,@RequestBody Reaction r){s.publicWork(id);if(!List.of("LIKE","BOOKMARK").contains(r.kind()))throw error(400,"未知操作");if(r.active())s.store().update("INSERT IGNORE INTO reactions(member_id,work_id,kind) VALUES(?,?,?)",s.memberId(p.getName()),id,r.kind());else s.store().update("DELETE FROM reactions WHERE member_id=? AND work_id=? AND kind=?",s.memberId(p.getName()),id,r.kind());}
    @GetMapping("/api/public/works/{id}/comments")public List<Map<String,Object>> comments(@PathVariable long id){s.publicWork(id);return s.store().list("SELECT c.id,c.body,c.created_at,c.member_id,m.pen_name,m.avatar_url FROM comments c JOIN members m ON m.id=c.member_id WHERE c.work_id=? AND c.visible=TRUE ORDER BY c.id DESC LIMIT 100",id);}
    public record TextBody(String body){}
    @PostMapping("/api/member/works/{id}/comments")public void comment(@PathVariable long id,Principal p,@RequestBody TextBody form){permissions.require(p.getName(),"COMMENT");s.publicWork(id);String body=bounded(form.body(),1000,"评论");if(body.isBlank())throw error(400,"请写下评论");checkText(body);s.store().insert("INSERT INTO comments(member_id,work_id,body) VALUES(?,?,?)",s.memberId(p.getName()),id,body);}
    @DeleteMapping("/api/member/comments/{id}")public void removeComment(@PathVariable long id,Principal p){var m=s.member(p.getName());if("ADMIN".equals(m.get("role")))s.store().update("UPDATE comments SET visible=FALSE WHERE id=?",id);else s.store().update("UPDATE comments SET visible=FALSE WHERE id=? AND member_id=?",id,m.get("id"));}
    public record Profile(String penName,String bio,String signature,String avatarUrl){}
    @PutMapping("/api/member/profile")public void profile(Principal p,@RequestBody Profile f){
        String name=bounded(f.penName(),40,"笔名");if(name.isBlank())name=p.getName();String avatar=Objects.requireNonNullElse(f.avatarUrl(),"");
        if(!avatar.isBlank()&&(!avatar.matches("/api/media/[a-f0-9-]{36}")||s.store().count("SELECT COUNT(*) FROM media_files WHERE id=? AND owner_id=?",avatar.substring(avatar.lastIndexOf('/')+1),s.memberId(p.getName()))==0))throw error(400,"请使用自己上传的头像");
        s.store().update("UPDATE members SET pen_name=?,bio=?,signature=?,avatar_url=?,profile_completed=TRUE,profile_prompt_seen=TRUE WHERE username=?",name,bounded(f.bio(),300,"简介"),bounded(f.signature(),120,"签名"),avatar,p.getName());
    }
    @PostMapping("/api/member/profile/reminder-dismiss")public void dismissProfile(Principal p){s.store().update("UPDATE members SET profile_prompt_seen=TRUE WHERE username=?",p.getName());}
    @GetMapping("/api/admin/overview")public Map<String,Object> overview(){return Map.of("members",s.store().list("SELECT id,username,pen_name,role,created_at FROM members ORDER BY id DESC"),"works",s.decorate(s.store().list(WORK_SELECT+"ORDER BY w.id DESC LIMIT 100"),false),"invitations",s.store().list("SELECT id,label,max_uses,used_count,active,expires_at FROM invitations ORDER BY id DESC LIMIT 100"));}
    public record Invite(String label,int maxUses){}
    @PostMapping("/api/admin/invitations")public Map<String,String> invite(@RequestBody Invite f){String code=UUID.randomUUID().toString().replace("-","").substring(0,16);if(f.maxUses()<1||f.maxUses()>500)throw error(400,"使用次数需为 1～500");s.store().insert("INSERT INTO invitations(code_hash,label,max_uses,expires_at) VALUES(?,?,?,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 30 DAY))",AuthController.hash(code),bounded(f.label(),80,"备注"),f.maxUses());return Map.of("code",code);}
    @DeleteMapping("/api/admin/invitations/{id}")public void revoke(@PathVariable long id){s.store().update("UPDATE invitations SET active=FALSE WHERE id=?",id);}
    public record TagInput(String name,String description,boolean isActivity){}
    @PostMapping("/api/admin/tags")public void tag(@RequestBody TagInput f){String name=bounded(f.name(),30,"标签");if(name.startsWith("合集：")||name.startsWith("合集:"))throw error(400,"合集标签请在投稿或合集编辑页设置");if(name.isBlank())throw error(400,"请填写标签名称");s.store().insert("INSERT INTO tags(name,description,is_activity) VALUES(?,?,?)",name,bounded(f.description(),300,"说明"),f.isActivity());}
    @PostMapping("/api/admin/works/{id}/withdraw")public void adminWithdraw(@PathVariable long id){s.store().update("UPDATE works SET status='WITHDRAWN' WHERE id=?",id);}
}
