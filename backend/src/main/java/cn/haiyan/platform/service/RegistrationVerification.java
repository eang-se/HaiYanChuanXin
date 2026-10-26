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
 private String eligible(String value){policy.requireOpen();String qq=RegistrationPolicy.qq(value);if(!eligibility.accepts(qq))throw error(400,policy.testMode()?"测试期间请填写 QQ 号 12345":"此 QQ 号尚未开放注册");return qq;}
 @Transactional public Map<String,Object> send(String value,String channel){
  String qq=eligible(value);if(!java.util.List.of("email","bot").contains(channel==null?"":channel))throw error(400,"请选择邮箱或机器人验证");
  db.update("INSERT IGNORE INTO registration_codes(qq_number,code_hash,expires_at,sent_at) VALUES(?,'',CURRENT_TIMESTAMP,DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 DAY))",qq);
  db.one("SELECT qq_number FROM registration_codes WHERE qq_number=? FOR UPDATE",qq);
  if(db.count("SELECT COUNT(*) FROM registration_codes WHERE qq_number=? AND sent_at>DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 60 SECOND)",qq)>0)throw error(429,"请在 60 秒后重新获取验证码");
  String code=policy.testMode()?"0000":String.format("%06d",new SecureRandom().nextInt(1000000));
  if(!policy.testMode())delivery.send(qq,code,channel);
  db.update("UPDATE registration_codes SET code_hash=?,expires_at=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 10 MINUTE),sent_at=CURRENT_TIMESTAMP,attempts=0,consumed=FALSE WHERE qq_number=?",AuthController.hash(code),qq);
  return Map.of("testMode",policy.testMode(),"retryAfter",60,"message",policy.testMode()?"测试验证码：0000":"验证码已发送");
 }
 @Transactional(noRollbackFor=ResponseStatusException.class) public String verify(String value,String code){
  String qq=eligible(value);
  if(policy.testMode()){if(!"0000".equals(code))throw error(400,"验证码不正确，测试期间请填写 0000");return qq;}
  var row=db.one("SELECT * FROM registration_codes WHERE qq_number=? FOR UPDATE",qq);
  if(row==null||db.count("SELECT COUNT(*) FROM registration_codes WHERE qq_number=? AND expires_at>CURRENT_TIMESTAMP AND consumed=FALSE AND attempts<5",qq)==0)throw error(400,"验证码已失效，请重新获取");
  if(!AuthController.hash(code==null?"":code).equals(row.get("codeHash"))){db.update("UPDATE registration_codes SET attempts=attempts+1 WHERE qq_number=?",qq);throw error(400,"验证码不正确");}
  db.update("UPDATE registration_codes SET consumed=TRUE WHERE qq_number=?",qq);return qq;
 }
}
