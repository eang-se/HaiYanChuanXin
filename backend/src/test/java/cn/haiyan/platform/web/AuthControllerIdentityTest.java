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
 @Test void publicAccountCannotAuthenticateDirectly(){
  var community=mock(CommunityService.class);var store=mock(CommunityStore.class);var encoder=mock(PasswordEncoder.class);
  when(community.store()).thenReturn(store);
  when(store.one("SELECT * FROM members WHERE username=?","shared")).thenReturn(Map.of("accountType","PUBLIC","passwordHash","hash"));
  var controller=new AuthController(community,encoder,mock(RegistrationPolicy.class),mock(RegistrationVerification.class),mock(PasswordResetService.class),mock(IdentityService.class),mock(AccountPermissions.class),mock(RememberedDeviceService.class));
  var error=assertThrows(ResponseStatusException.class,()->controller.login(new AuthController.Login("shared","password"),new MockHttpServletRequest(),new MockHttpServletResponse()));
  assertEquals(401,error.getStatusCode().value());verifyNoInteractions(encoder);
 }
}
