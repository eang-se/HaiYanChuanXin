package cn.haiyan.platform.service;
/** 正式接入时实现此接口：向 qq@qq.com 发邮件，或向该 QQ 发送机器人私信。禁止将验证码写入日志。 */
public interface VerificationDelivery { void send(String qq,String code,String channel); }
