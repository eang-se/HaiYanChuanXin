package cn.haiyan.platform.service;
import java.security.SecureRandom;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import cn.haiyan.platform.web.AuthController;
import static cn.haiyan.platform.service.CommunityService.*;
@Service
public class RegistrationVerification {
 private final CommunityStore db;private final RegistrationPolicy policy;private final QqEligibility eligibility;private final VerificationDelivery delivery;
 public RegistrationVerification(CommunityStore db,RegistrationPolicy policy,QqEligibility eligibility,VerificationDelivery delivery){this.db=db;this.policy=policy;this.eligibility=eligibility;this.delivery=delivery;}
 private String eligible(String value){policy.requireOpen();String qq=RegistrationPolicy.qq(value);if(!eligibility.accepts(qq))throw error(400,"此 QQ 号不在注册名单中，请联系管理员");if(!policy.isTestQq(qq)&&db.count("SELECT COUNT(*) FROM qq_invitation_bindings WHERE qq_number=?",qq)>0)throw error(400,"此 QQ 号已绑定账号，不能重复注册");return qq;}
 public String defaultName(String qq,String username){if(policy.isTestQq(qq))return username;String name=eligibility.displayName(qq);if(name==null||name.isBlank())throw error(400,"此 QQ 号已不在注册名单中，请联系管理员");return name;}
 @Transactional(noRollbackFor=ResponseStatusException.class) public Map<String,Object> send(String value,String channel){
  String qq=eligible(value);if(!"email".equals(channel))throw error(400,"仅支持邮箱验证码");
  db.update("INSERT IGNORE INTO registration_codes(qq_number,code_hash,expires_at,sent_at) VALUES(?,'',CURRENT_TIMESTAMP,DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 DAY))",qq);
  db.one("SELECT qq_number FROM registration_codes WHERE qq_number=? FOR UPDATE",qq);
  if(db.count("SELECT COUNT(*) FROM registration_codes WHERE qq_number=? AND sent_at>DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 60 SECOND)",qq)>0)throw error(429,"请在 60 秒后重新获取验证码");
  db.update("UPDATE registration_codes SET sent_at=CURRENT_TIMESTAMP WHERE qq_number=?",qq);
  boolean isTest=policy.isTestQq(qq);
  String code=isTest?"0000":String.format(java.util.Locale.ROOT,"%06d",new SecureRandom().nextInt(1000000));
  if(!isTest)delivery.send(qq,code,channel);
  db.update("UPDATE registration_codes SET code_hash=?,expires_at=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 10 MINUTE),sent_at=CURRENT_TIMESTAMP,attempts=0,consumed=FALSE WHERE qq_number=?",AuthController.hash(code),qq);
  return Map.of("testMode",isTest,"retryAfter",60,"message",isTest?"测试验证码：0000":"验证码已发送至同名 QQ 邮箱");
 }
 @Transactional(noRollbackFor=ResponseStatusException.class) public String verify(String value,String code){
  String qq=eligible(value);
  if(policy.isTestQq(qq)){if(!"0000".equals(code))throw error(400,"验证码不正确，测试账号请填写 0000");return qq;}
  // A locking read evaluates expiry and consumption against the latest committed row,
  // even when the surrounding registration transaction already has a MySQL snapshot.
  var row=db.one("SELECT code_hash FROM registration_codes WHERE qq_number=? AND expires_at>CURRENT_TIMESTAMP AND consumed=FALSE AND attempts<5 FOR UPDATE",qq);
  if(row==null)throw error(400,"验证码已失效，请重新获取");
  if(!AuthController.hash(code==null?"":code).equals(row.get("codeHash"))){db.update("UPDATE registration_codes SET attempts=attempts+1 WHERE qq_number=?",qq);throw error(400,"验证码不正确");}
  db.update("UPDATE registration_codes SET consumed=TRUE WHERE qq_number=?",qq);return qq;
 }
}
