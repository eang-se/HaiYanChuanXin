package cn.haiyan.platform.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.haiyan.platform.web.AuthController;
import static cn.haiyan.platform.service.CommunityService.*;

@Service
public class RememberedDeviceService {
 private static final String COOKIE="haiyan_remember";
 private final CommunityStore db;private final SecureRandom random=new SecureRandom();
 public RememberedDeviceService(CommunityStore db){this.db=db;}
 private boolean allowed(HttpServletRequest req){
  String host=Objects.requireNonNullElse(req.getServerName(),"").toLowerCase(Locale.ROOT);
  String remote=Objects.requireNonNullElse(req.getRemoteAddr(),"");
  return req.isSecure()||(Set.of("localhost","127.0.0.1","::1","[::1]","0:0:0:0:0:0:0:1").contains(host)&&Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1").contains(remote));
 }
 private String[] cookie(HttpServletRequest req){
  if(req.getCookies()==null)return null;
  for(Cookie cookie:req.getCookies())if(COOKIE.equals(cookie.getName())&&cookie.getValue().matches("[a-f0-9]{32}\\.[a-f0-9]{64}"))return cookie.getValue().split("\\.");
  return null;
 }
 private String randomHex(int bytes){byte[] value=new byte[bytes];random.nextBytes(value);return HexFormat.of().formatHex(value);}
 private void setCookie(HttpServletRequest req,HttpServletResponse res,String value,Duration age){
  res.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(COOKIE,value).httpOnly(true).secure(req.isSecure()).sameSite("Lax").path("/api/auth").maxAge(age).build().toString());
 }
 private Map<String,Object> lookup(HttpServletRequest req,boolean lock){
  if(!allowed(req))return null;String[] value=cookie(req);if(value==null)return null;
  var row=db.one("SELECT d.selector,d.token_hash,d.credential_hash,m.id,m.username,m.pen_name,m.qq_number,m.password_hash,m.account_type,m.role FROM remembered_devices d JOIN members m ON m.id=d.member_id WHERE d.selector=? AND d.expires_at>CURRENT_TIMESTAMP"+(lock?" FOR UPDATE":""),value[0]);
  if(row==null||!"PERSONAL".equals(row.get("accountType")))return null;
  String supplied=AuthController.hash(value[1]),stored=String.valueOf(row.get("tokenHash"));
  if(!MessageDigest.isEqual(supplied.getBytes(StandardCharsets.US_ASCII),stored.getBytes(StandardCharsets.US_ASCII)))return null;
  if(!AuthController.hash(String.valueOf(row.get("passwordHash"))).equals(row.get("credentialHash")))return null;
  return row;
 }
 public Map<String,Object> describe(HttpServletRequest req){
  var row=lookup(req,false);if(row==null)return Map.of();
  return Map.of("username",row.get("username"),"penName",row.get("penName"),"avatarUrl",avatarUrl(row.get("id"),row.get("qqNumber")));
 }
 @Transactional public void remember(HttpServletRequest req,HttpServletResponse res,Map<String,Object> member){
  if(!allowed(req))throw error(400,"记住此设备需要本机访问或 HTTPS");
  forget(req,res);String selector=randomHex(16),secret=randomHex(32);
  db.update("INSERT INTO remembered_devices(selector,token_hash,credential_hash,member_id,expires_at) VALUES(?,?,?,?,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 30 DAY))",selector,AuthController.hash(secret),AuthController.hash(String.valueOf(member.get("passwordHash"))),member.get("id"));
  setCookie(req,res,selector+"."+secret,Duration.ofDays(30));
 }
 @Transactional public Map<String,Object> use(HttpServletRequest req,HttpServletResponse res){
  var row=lookup(req,true);if(row==null)throw error(401,"快捷登录已失效，请输入密码登录");
  String secret=randomHex(32);db.update("UPDATE remembered_devices SET token_hash=?,expires_at=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 30 DAY) WHERE selector=?",AuthController.hash(secret),row.get("selector"));
  setCookie(req,res,row.get("selector")+"."+secret,Duration.ofDays(30));return row;
 }
 @Transactional public void forget(HttpServletRequest req,HttpServletResponse res){
  String[] value=cookie(req);if(value!=null)db.update("DELETE FROM remembered_devices WHERE selector=? AND token_hash=?",value[0],AuthController.hash(value[1]));
  setCookie(req,res,"",Duration.ZERO);
 }
}
