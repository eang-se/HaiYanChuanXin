package cn.haiyan.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static cn.haiyan.platform.service.CommunityService.error;

/** Sends a registration code only to the mailbox belonging to the invited QQ number. */
public class QqEmailDelivery implements VerificationDelivery {
 private static final Logger log=LoggerFactory.getLogger(QqEmailDelivery.class);
 private final JavaMailSender sender;private final String from;
 public QqEmailDelivery(JavaMailSender sender,String from){this.sender=sender;this.from=from;}
 @Override public void send(String qq,String code,String channel){
  if(!"email".equals(channel))throw error(400,"请使用 QQ 邮箱验证码");
  sendMessage(qq,code,false);
 }
 @Override public void sendPasswordReset(String qq,String code){sendMessage(qq,code,true);}
 private void sendMessage(String qq,String code,boolean reset){
  String recipient=RegistrationPolicy.qq(qq)+"@qq.com";
  if(code==null||!code.matches("[0-9]{6}"))throw error(500,"验证码生成失败");
  var message=new SimpleMailMessage();
  message.setFrom(from);message.setTo(recipient);message.setSubject(reset?"海燕传信 · 密码找回验证码":"海燕传信 · 注册验证码");
  message.setText((reset?"您正在重设海燕传信账号密码。":"您正在注册海燕传信。")+"\n\n验证码："+code+"\n\n验证码 10 分钟内有效，且只能使用一次。请勿向他人提供。\n如非本人操作，请忽略此邮件。\n");
  try{sender.send(message);}catch(MailException e){log.warn("Registration email could not be sent ({})",e.getClass().getSimpleName());throw error(503,"邮件发送失败，请稍后重试或联系管理员");}
 }
}
