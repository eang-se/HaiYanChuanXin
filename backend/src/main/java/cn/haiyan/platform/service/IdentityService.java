package cn.haiyan.platform.service;

import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.haiyan.platform.web.AuthController;
import static cn.haiyan.platform.service.CommunityService.error;

/** A public identity is a session-scoped delegation, never an independently authenticated account. */
@Service
public class IdentityService {
 public static final String ACTOR="actorUsername", SELECTED="identityId";
 private final CommunityStore db;
 public IdentityService(CommunityStore db){this.db=db;}
 private String hash(HttpSession session){return AuthController.hash(session.getId());}
 public String actor(HttpSession session,String fallback){Object value=session==null?null:session.getAttribute(ACTOR);return value instanceof String s?s:fallback;}
 public List<Map<String,Object>> available(long actorId){return db.list("SELECT m.id,m.username FROM members m WHERE m.id=? OR (m.account_type='PUBLIC' AND EXISTS(SELECT 1 FROM public_account_members pam WHERE pam.public_member_id=m.id AND pam.member_id=?)) ORDER BY m.account_type='PUBLIC',m.id",actorId,actorId);}
 @Transactional
 public String select(HttpSession session,long actorId,long targetId){
  var target=db.one("SELECT id,username,account_type FROM members WHERE id=? FOR UPDATE",targetId);
  if(target==null)throw error(404,"身份不存在");
  boolean personal=actorId==targetId && "PERSONAL".equals(target.get("accountType"));
  if(!personal&&(!"PUBLIC".equals(target.get("accountType"))||db.one("SELECT member_id FROM public_account_members WHERE public_member_id=? AND member_id=? FOR UPDATE",targetId,actorId)==null))throw error(403,"没有此公用身份的使用权限");
  String token=hash(session);
  if(!personal){
   db.update("DELETE FROM public_account_sessions WHERE public_member_id=? AND last_seen<DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 30 MINUTE)",targetId);
   var leases=db.list("SELECT actor_id FROM public_account_sessions WHERE public_member_id=? FOR UPDATE",targetId);
   Set<Long> users=new HashSet<>();for(var lease:leases)users.add(((Number)lease.get("actorId")).longValue());
   if(!users.contains(actorId)&&users.size()>=5)throw error(409,"该公用身份已有 5 位成员正在使用，请稍后再试");
  }
  db.update("DELETE FROM public_account_sessions WHERE session_hash=?",token);
  if(!personal)db.update("INSERT INTO public_account_sessions(public_member_id,actor_id,session_hash,last_seen) VALUES(?,?,?,CURRENT_TIMESTAMP(6))",targetId,actorId,token);
  session.setAttribute(SELECTED,targetId);
  return (String)target.get("username");
 }
 /** Refresh the lease on every authenticated request; revoked or expired delegations return to personal. */
 public String current(HttpSession session,long actorId,String actorUsername){
  Object selected=session.getAttribute(SELECTED);if(!(selected instanceof Number))return actorUsername;
  long target=((Number)selected).longValue();if(target==actorId)return actorUsername;
  var allowed=db.one("SELECT m.username FROM members m JOIN public_account_members p ON p.public_member_id=m.id WHERE m.id=? AND m.account_type='PUBLIC' AND p.member_id=?",target,actorId);
  if(allowed!=null&&db.update("UPDATE public_account_sessions SET last_seen=CURRENT_TIMESTAMP(6) WHERE public_member_id=? AND actor_id=? AND session_hash=? AND last_seen>=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 30 MINUTE)",target,actorId,hash(session))>0)return (String)allowed.get("username");
  release(session);session.removeAttribute(SELECTED);return actorUsername;
 }
 public void release(HttpSession session){db.update("DELETE FROM public_account_sessions WHERE session_hash=?",hash(session));}
 @EventListener public void sessionDestroyed(HttpSessionDestroyedEvent event){try{db.update("DELETE FROM public_account_sessions WHERE session_hash=?",AuthController.hash(event.getId()));}catch(RuntimeException ignored){/* Expired leases are reclaimed when the identity is next selected. */}}
}
