package cn.haiyan.platform.service;

import org.springframework.stereotype.Service;
import static cn.haiyan.platform.service.CommunityService.*;

@Service
public class RegistrationPolicy {
 private final CommunityStore db;
 public RegistrationPolicy(CommunityStore db){this.db=db;}
 public String setting(String key,String fallback){var r=db.one("SELECT setting_value FROM platform_metadata WHERE setting_key=?",key);return r==null?fallback:(String)r.get("settingValue");}
 public boolean enabled(){return Boolean.parseBoolean(setting("registration_enabled","true"));}
 public boolean testMode(){return "test".equals(setting("registration_mode","test"));}
 public boolean isTestQq(String qq){return testMode()&&"12345".equals(qq);}
 public void requireOpen(){if(!enabled())throw error(403,"注册暂未开放");}
 public static String qq(String value){String qq=bounded(value,12,"QQ 号");if(!qq.matches("[1-9][0-9]{4,11}"))throw error(400,"请输入 5～12 位数字 QQ 号");return qq;}
}
