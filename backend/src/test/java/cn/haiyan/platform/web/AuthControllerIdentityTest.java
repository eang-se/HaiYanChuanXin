package cn.haiyan.platform.web;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import cn.haiyan.platform.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerIdentityTest {
 @Test void publicIdentityIncludesThePersonalOperatorName(){
  var community=mock(CommunityService.class);var identities=mock(IdentityService.class);var permissions=mock(AccountPermissions.class);
  var request=new MockHttpServletRequest();var session=request.getSession();
  when(identities.actor(session,"haiyan_editor")).thenReturn("personal_writer");
  when(community.member("haiyan_editor")).thenReturn(new java.util.HashMap<>(Map.of("id",29L,"username","haiyan_editor","penName","海燕编辑部","accountType","PUBLIC")));
  when(community.member("personal_writer")).thenReturn(Map.of("id",1L,"username","personal_writer","penName","个人昵称","role","MEMBER"));
  var controller=new AuthController(community,mock(PasswordEncoder.class),mock(RegistrationPolicy.class),mock(RegistrationVerification.class),mock(PasswordResetService.class),identities,permissions,mock(RememberedDeviceService.class));
  var current=controller.me(()->"haiyan_editor",request);
  assertEquals("海燕编辑部",current.get("penName"));assertEquals("PUBLIC",current.get("accountType"));
  assertEquals("个人昵称",current.get("actorPenName"));assertEquals("personal_writer",current.get("actorUsername"));assertEquals(1L,current.get("actorId"));
 }
 @Test void publicAccountCannotAuthenticateDirectly(){
  var community=mock(CommunityService.class);var store=mock(CommunityStore.class);var encoder=mock(PasswordEncoder.class);
  when(community.store()).thenReturn(store);
  when(store.one("SELECT * FROM members WHERE username=? AND deleted_at IS NULL","shared")).thenReturn(Map.of("accountType","PUBLIC","passwordHash","hash"));
  var controller=new AuthController(community,encoder,mock(RegistrationPolicy.class),mock(RegistrationVerification.class),mock(PasswordResetService.class),mock(IdentityService.class),mock(AccountPermissions.class),mock(RememberedDeviceService.class));
  var error=assertThrows(ResponseStatusException.class,()->controller.login(new AuthController.Login("shared","password"),new MockHttpServletRequest(),new MockHttpServletResponse()));
  assertEquals(401,error.getStatusCode().value());verifyNoInteractions(encoder);
 }
}
