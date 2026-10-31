package cn.haiyan.platform.config;
import cn.haiyan.platform.service.*;
import cn.haiyan.platform.web.AdminSettingsController;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AccountPermissionsTest {
 @Test void memberOverridesApplyAndRestrictedDefaultsToNoPermissions(){
  var db=mock(CommunityStore.class);var permissions=new AccountPermissions(db);
  when(db.list("SELECT permission_code,allowed FROM member_permissions WHERE member_id=?",2L)).thenReturn(List.of(Map.of("permissionCode","WRITE","allowed",false)));
  assertFalse(permissions.effective(2L,"MEMBER").get("WRITE"));assertTrue(permissions.effective(2L,"MEMBER").get("COMMENT"));assertTrue(permissions.effective(2L,"ADMIN").get("WRITE"));assertTrue(permissions.effective(2L,"SUPER_ADMIN").get("WRITE"));
  assertFalse(permissions.effective(3L,"RESTRICTED").get("WRITE"));
 }
 @Test void publicIdentityCannotBypassRestrictionsOnItsPersonalActor(){
  var db=mock(CommunityStore.class);var permissions=new AccountPermissions(db);var request=new MockHttpServletRequest();request.getSession().setAttribute("actorUsername","member");
  RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  try{
   when(db.one("SELECT id,role,account_type FROM members WHERE username=?","public")).thenReturn(Map.of("id",10L,"role","MEMBER","accountType","PUBLIC"));
   when(db.one("SELECT id,role,account_type FROM members WHERE username=?","member")).thenReturn(Map.of("id",11L,"role","RESTRICTED","accountType","PERSONAL"));
   assertFalse(permissions.currentEffective("public").get("WRITE"));
   assertThrows(ResponseStatusException.class,()->permissions.require("public","WRITE"));
  }finally{RequestContextHolder.resetRequestAttributes();}
 }
 @Test void lastSuperAdministratorCannotBeDemoted(){
  var db=mock(CommunityStore.class);var controller=new AdminSettingsController(db,mock(RegistrationPolicy.class),new AccountPermissions(db),mock(MarkdownQqDirectory.class));
  when(db.one("SELECT id,role,account_type FROM members WHERE username=?","admin")).thenReturn(Map.of("id",1L,"role","SUPER_ADMIN","accountType","PERSONAL"));
  when(db.list("SELECT id FROM members WHERE role='SUPER_ADMIN' AND account_type='PERSONAL' FOR UPDATE")).thenReturn(List.of(Map.of("id",1L)));
  when(db.one("SELECT id,role,account_type FROM members WHERE id=? FOR UPDATE",1L)).thenReturn(Map.of("id",1L,"role","SUPER_ADMIN","accountType","PERSONAL"));
  var error=assertThrows(ResponseStatusException.class,()->controller.update(1L,new AdminSettingsController.PermissionInput("MEMBER",Map.of("WRITE",false)),()->"admin"));
  assertEquals(400,error.getStatusCode().value());verify(db,never()).update("UPDATE members SET role=? WHERE id=?","MEMBER",1L);
 }
 @Test void regularAdministratorCannotPromoteOrEditAnotherAdministrator(){
  var db=mock(CommunityStore.class);var controller=new AdminSettingsController(db,mock(RegistrationPolicy.class),new AccountPermissions(db),mock(MarkdownQqDirectory.class));
  when(db.one("SELECT id,role,account_type FROM members WHERE username=?","admin")).thenReturn(Map.of("id",1L,"role","ADMIN","accountType","PERSONAL"));
  when(db.one("SELECT id,role,account_type FROM members WHERE id=? FOR UPDATE",2L)).thenReturn(Map.of("id",2L,"role","MEMBER","accountType","PERSONAL"));
  var error=assertThrows(ResponseStatusException.class,()->controller.update(2L,new AdminSettingsController.PermissionInput("ADMIN",Map.of()),()->"admin"));
  assertEquals(403,error.getStatusCode().value());verify(db,never()).update("UPDATE members SET role=? WHERE id=?","ADMIN",2L);
 }
 @Test void regularAdministratorCannotModifyPublicIdentityWhitelist(){
  var db=mock(CommunityStore.class);var controller=new AdminSettingsController(db,mock(RegistrationPolicy.class),new AccountPermissions(db),mock(MarkdownQqDirectory.class));
  when(db.one("SELECT id,role,account_type FROM members WHERE username=?","admin")).thenReturn(Map.of("id",1L,"role","ADMIN","accountType","PERSONAL"));
  var error=assertThrows(ResponseStatusException.class,()->controller.publicMembers(20L,new AdminSettingsController.PublicMembers(List.of(1L)),()->"admin"));
  assertEquals(403,error.getStatusCode().value());verify(db,never()).update("DELETE FROM public_account_members WHERE public_member_id=?",20L);
 }
}
