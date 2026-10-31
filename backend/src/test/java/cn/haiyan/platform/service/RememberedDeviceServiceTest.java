package cn.haiyan.platform.service;
import java.util.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import cn.haiyan.platform.web.AuthController;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RememberedDeviceServiceTest {
 private final CommunityStore db=mock(CommunityStore.class);
 private final RememberedDeviceService remembered=new RememberedDeviceService(db);
 private static final String QUERY="SELECT d.selector,d.token_hash,d.credential_hash,m.id,m.username,m.pen_name,m.qq_number,m.password_hash,m.account_type,m.role FROM remembered_devices d JOIN members m ON m.id=d.member_id WHERE d.selector=? AND d.expires_at>CURRENT_TIMESTAMP";
 private MockHttpServletRequest local(){var req=new MockHttpServletRequest();req.setServerName("127.0.0.1");req.setRemoteAddr("127.0.0.1");return req;}
 @Test void deviceCookieIsHttpOnlyAndTokenIsStoredAsHash(){
  var request=local();var response=new MockHttpServletResponse();
  remembered.remember(request,response,Map.of("id",1L,"passwordHash","bcrypt"));
  var cookies=response.getHeaders("Set-Cookie");String value=cookies.getLast();assertTrue(value.contains("HttpOnly"));assertTrue(value.contains("SameSite=Lax"));
  String raw=value.substring(value.indexOf('=')+1,value.indexOf(';'));String[] parts=raw.split("\\.");assertEquals(2,parts.length);
  verify(db).update(contains("INSERT INTO remembered_devices"),eq(parts[0]),eq(AuthController.hash(parts[1])),eq(AuthController.hash("bcrypt")),eq(1L));
 }
 @Test void rememberedLoginRotatesSecretAndPasswordChangeInvalidates(){
  String selector="a".repeat(32),secret="b".repeat(64);var request=local();request.setCookies(new Cookie("haiyan_remember",selector+"."+secret));
  var row=new HashMap<String,Object>(Map.of("selector",selector,"tokenHash",AuthController.hash(secret),"credentialHash",AuthController.hash("bcrypt"),"passwordHash","bcrypt","accountType","PERSONAL","id",1L,"username","alice","penName","昵称","qqNumber","12345","role","MEMBER"));
  when(db.one(QUERY,selector)).thenReturn(row);when(db.one(QUERY+" FOR UPDATE",selector)).thenReturn(row);
  assertEquals("alice",remembered.describe(request).get("username"));var response=new MockHttpServletResponse();assertEquals("alice",remembered.use(request,response).get("username"));
  String next=response.getHeader("Set-Cookie");assertNotNull(next);assertFalse(next.contains(secret));verify(db).update(startsWith("UPDATE remembered_devices SET token_hash"),anyString(),eq(selector));
  row.put("passwordHash","changed");assertTrue(remembered.describe(request).isEmpty());assertThrows(ResponseStatusException.class,()->remembered.use(request,new MockHttpServletResponse()));
 }
 @Test void unencryptedNonLocalHostCannotRememberDevice(){
  var request=local();request.setServerName("example.org");request.setRemoteAddr("192.0.2.2");
  assertThrows(ResponseStatusException.class,()->remembered.remember(request,new MockHttpServletResponse(),Map.of("id",1L,"passwordHash","bcrypt")));
  verifyNoInteractions(db);
 }
}
