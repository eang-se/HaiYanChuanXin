package cn.haiyan.platform.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import cn.haiyan.platform.web.AuthController;
import static cn.haiyan.platform.service.CommunityService.error;

@Service
public class PasswordResetService {
 private final CommunityStore db;private final RegistrationPolicy policy;private final VerificationDelivery delivery;private final PasswordEncoder encoder;
 public PasswordResetService(CommunityStore db,RegistrationPolicy policy,VerificationDelivery delivery,PasswordEncoder encoder){this.db=db;this.policy=policy;this.delivery=delivery;this.encoder=encoder;}
 private Map<String,Object> member(String username){
  if(username==null||!username.matches("[a-zA-Z0-9_]{3,30}"))return null;
  var m=db.one("SELECT id,qq_number FROM members WHERE username=? AND account_type='PERSONAL'",username);
  return m!=null&&m.get("qqNumber") instanceof String q&&q.matches("[1-9][0-9]{4,11}")?m:null;
 }
 @Transactional(noRollbackFor=ResponseStatusException.class)
 public Map<String,Object> send(String username){return send(username,"email");}
 @Transactional(noRollbackFor=ResponseStatusException.class)
 public Map<String,Object> send(String username,String requestedChannel){
  String channel=requestedChannel==null||requestedChannel.isBlank()?"email":requestedChannel;if(!"email".equals(channel))throw error(400,"仅支持邮箱验证码");
  var m=member(username);var result=Map.<String,Object>of("message","如果账号已绑定 QQ，验证码将发送至对应邮箱。", "retryAfter",60);
  if(m==null)return result;
  Object id=m.get("id");String qq=(String)m.get("qqNumber");
  db.update("INSERT IGNORE INTO password_reset_codes(member_id,qq_number,code_hash,expires_at,sent_at) VALUES(?,?,'',CURRENT_TIMESTAMP,DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 DAY))",id,qq);
  db.one("SELECT member_id FROM password_reset_codes WHERE member_id=? FOR UPDATE",id);
  if(db.count("SELECT COUNT(*) FROM password_reset_codes WHERE member_id=? AND sent_at>DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 60 SECOND)",id)>0)throw error(429,"请在 60 秒后重新获取验证码");
  db.update("UPDATE password_reset_codes SET sent_at=CURRENT_TIMESTAMP,consumed=TRUE WHERE member_id=?",id);
  boolean test=policy.isTestQq(qq);String code=test?"0000":String.format(Locale.ROOT,"%06d",new SecureRandom().nextInt(1000000));
  if(!test)delivery.sendPasswordReset(qq,code);
  db.update("UPDATE password_reset_codes SET qq_number=?,code_hash=?,expires_at=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 10 MINUTE),sent_at=CURRENT_TIMESTAMP,attempts=0,consumed=FALSE WHERE member_id=?",qq,AuthController.hash(code),id);
  return result;
 }
 @Transactional(noRollbackFor=ResponseStatusException.class)
 public void reset(String username,String code,String password){
  if(password==null||password.length()<10||password.getBytes(StandardCharsets.UTF_8).length>72)throw error(400,"密码至少 10 位，且不超过 72 字节");
  var m=member(username);if(m==null)throw error(400,"验证码无效或已过期");
  Object id=m.get("id");String qq=(String)m.get("qqNumber");
  var row=db.one("SELECT code_hash FROM password_reset_codes WHERE member_id=? AND qq_number=? AND expires_at>CURRENT_TIMESTAMP AND consumed=FALSE AND attempts<5 FOR UPDATE",id,qq);
  if(row==null)throw error(400,"验证码无效或已过期");
  if(!AuthController.hash(code==null?"":code).equals(row.get("codeHash"))){db.update("UPDATE password_reset_codes SET attempts=attempts+1 WHERE member_id=?",id);throw error(400,"验证码无效或已过期");}
  db.update("UPDATE members SET password_hash=? WHERE id=?",encoder.encode(password),id);
  db.update("UPDATE password_reset_codes SET consumed=TRUE WHERE member_id=?",id);
 }
}
