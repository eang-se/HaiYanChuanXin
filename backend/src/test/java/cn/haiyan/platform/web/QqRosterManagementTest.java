package cn.haiyan.platform.web;
import java.util.*;
import org.junit.jupiter.api.Test;
import cn.haiyan.platform.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class QqRosterManagementTest {
 @Test void duplicateMemberStatusIsSharedAcrossGroups(){
  var db=mock(CommunityStore.class);var roster=mock(MarkdownQqDirectory.class);
  when(roster.groups()).thenReturn(Map.of("group1",Map.of("234567","甲"),"group2",Map.of("234567","乙")));
  when(db.list("SELECT qq_number FROM qq_allowlist WHERE active=FALSE")).thenReturn(List.of(Map.of("qqNumber","234567")));
  when(db.list("SELECT qq_number FROM qq_invitation_bindings")).thenReturn(List.of(Map.of("qqNumber","234567")));
  var controller=new AdminSettingsController(db,mock(RegistrationPolicy.class),new AccountPermissions(db),roster);
  var rows=controller.qqs();assertEquals(2,rows.size());assertTrue(rows.stream().allMatch(r->Boolean.FALSE.equals(r.get("active"))&&Boolean.TRUE.equals(r.get("bound"))));
  controller.disable("group1","234567");verify(db).update(contains("INSERT INTO qq_allowlist"),eq("234567"));
  controller.importQqs(new AdminSettingsController.QqImport("group2",List.of("234567")));verify(db).update(contains("VALUES(?,TRUE)"),eq("234567"));
 }
}
