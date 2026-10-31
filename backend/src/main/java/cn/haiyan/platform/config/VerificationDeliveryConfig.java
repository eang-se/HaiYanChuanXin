package cn.haiyan.platform.config;
import cn.haiyan.platform.service.VerificationDelivery;
import cn.haiyan.platform.service.QqEmailDelivery;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import static cn.haiyan.platform.service.CommunityService.*;
@Configuration
public class VerificationDeliveryConfig {
 @Bean @ConditionalOnMissingBean(VerificationDelivery.class)
 VerificationDelivery verificationDelivery(@Value("${haiyan.mail.username:${HAIYAN_SMTP_USERNAME:}}") String username,
       @Value("${haiyan.mail.password:${HAIYAN_SMTP_PASSWORD:}}") String password,
       @Value("${haiyan.mail.realm-config:../realm/realm/application-local.properties}") String realmConfig){
  if(username.isBlank()&&password.isBlank()){
   Properties existing=new Properties();Path file=Path.of(realmConfig);
   if(Files.isRegularFile(file))try(Reader reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){existing.load(reader);}catch(IOException ignored){}
   username=existing.getProperty("email.smtp.email","").trim();password=existing.getProperty("email.smtp.code","").trim();
  }
  if(username.isBlank()||password.isBlank()||!username.matches("(?i)^[a-z0-9._%+\\-]+@(qq\\.com|foxmail\\.com|vip\\.qq\\.com)$"))
   return (qq,code,channel)->{throw error(503,"邮件发件账号未配置，请联系管理员");};
  var smtp=new JavaMailSenderImpl();smtp.setHost("smtp.qq.com");smtp.setPort(465);smtp.setUsername(username);smtp.setPassword(password);smtp.setDefaultEncoding("UTF-8");
  var properties=smtp.getJavaMailProperties();properties.setProperty("mail.smtp.auth","true");properties.setProperty("mail.smtp.ssl.enable","true");properties.setProperty("mail.smtp.ssl.checkserveridentity","true");properties.setProperty("mail.smtp.connectiontimeout","5000");properties.setProperty("mail.smtp.timeout","10000");properties.setProperty("mail.smtp.writetimeout","10000");
  return new QqEmailDelivery(smtp,username);
 }
}
