package cn.haiyan.platform.service;
/** 向同名 QQ 邮箱发送注册验证码。禁止将验证码写入日志。 */
public interface VerificationDelivery { void send(String qq,String code,String channel); default void sendPasswordReset(String qq,String code){send(qq,code,"email");} }
