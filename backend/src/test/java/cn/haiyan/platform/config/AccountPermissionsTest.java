package cn.haiyan.platform.config;
import cn.haiyan.platform.service.*;
import cn.haiyan.platform.web.AdminSettingsController;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AccountPermissionsTest {
 @Test void memberOverridesApplyButAdminsKeepPermissions(){
  var db=mock(CommunityStore.class);var permissions=new AccountPermissions(db);
  when(db.list("SELECT permission_code,allowed FROM member_permissions WHERE member_id=?",2L)).thenReturn(List.of(Map.of("permissionCode","WRITE","allowed",false)));
  assertFalse(permissions.effective(2L,"MEMBER").get("WRITE"));assertTrue(permissions.effective(2L,"MEMBER").get("COMMENT"));assertTrue(permissions.effective(2L,"ADMIN").get("WRITE"));
 }
 @Test void lastAdministratorCannotBeDemoted(){
  var db=mock(CommunityStore.class);var controller=new AdminSettingsController(db,mock(RegistrationPolicy.class),new AccountPermissions(db));
  when(db.list("SELECT id FROM members WHERE role='ADMIN' FOR UPDATE")).thenReturn(List.of(Map.of("id",1L)));
  when(db.one("SELECT id,role FROM members WHERE id=? FOR UPDATE",1L)).thenReturn(Map.of("id",1L,"role","ADMIN"));
  var error=assertThrows(ResponseStatusException.class,()->controller.update(1L,new AdminSettingsController.PermissionInput("MEMBER",Map.of("WRITE",false)),()->"admin"));
  assertEquals(400,error.getStatusCode().value());
  verify(db,never()).update("UPDATE members SET role=? WHERE id=?","MEMBER",1L);
 }
}
