package cn.haiyan.platform.config;
import cn.haiyan.platform.service.VerificationDelivery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import static cn.haiyan.platform.service.CommunityService.*;
@Configuration
public class VerificationDeliveryConfig {
 @Bean @ConditionalOnMissingBean(VerificationDelivery.class)
 VerificationDelivery verificationDelivery(){return (qq,code,channel)->{throw error(503,"正式验证码发送服务尚未配置");};}
}
