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
import static org.mockito.ArgumentMatchers.*;

class AccountDeletionTest {
    final CommunityStore db=mock(CommunityStore.class);
    final AdminSettingsController controller=new AdminSettingsController(db,mock(RegistrationPolicy.class),mock(AccountPermissions.class),mock(MarkdownQqDirectory.class));
    void setup(String role,String type){
        when(db.one("SELECT id,role,account_type FROM members WHERE username=? AND deleted_at IS NULL","root")).thenReturn(Map.of("id",1L,"role",role,"accountType","PERSONAL"));
        when(db.one("SELECT id,role,account_type FROM members WHERE id=? AND deleted_at IS NULL FOR UPDATE",2L)).thenReturn(Map.of("id",2L,"role","MEMBER","accountType",type));
        when(db.list("SELECT id FROM members WHERE role='SUPER_ADMIN' AND account_type='PERSONAL' AND deleted_at IS NULL FOR UPDATE")).thenReturn(List.of(Map.of("id",1L)));
    }
    @Test void publicIdentityUsesThePersonalSuperAdministratorAndRevokesAllLoginDelegations(){
        setup("SUPER_ADMIN","PERSONAL");var request=new MockHttpServletRequest();request.getSession().setAttribute("actorUsername","root");RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try{controller.deleteAccount(2L,()->"haiyan_activity");}finally{RequestContextHolder.resetRequestAttributes();}
        verify(db).update("DELETE FROM public_account_sessions WHERE actor_id=?",2L);verify(db).update("DELETE FROM public_account_members WHERE member_id=?",2L);verify(db).update("DELETE FROM remembered_devices WHERE member_id=?",2L);
        verify(db).update("UPDATE members SET deleted_at=CURRENT_TIMESTAMP,role='RESTRICTED' WHERE id=? AND deleted_at IS NULL",2L);
        verify(db,never()).update(startsWith("DELETE FROM works"),any(Object[].class));
    }
    @Test void administratorsCannotDeleteUsersAndPublicAccountsCannotBeDeleted(){
        setup("ADMIN","PERSONAL");assertThrows(ResponseStatusException.class,()->controller.deleteAccount(2L,()->"root"));
        setup("SUPER_ADMIN","PUBLIC");assertThrows(ResponseStatusException.class,()->controller.deleteAccount(2L,()->"root"));verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void selfDeletionAndRemovingTheLastSuperAdministratorAreRejected(){
        setup("SUPER_ADMIN","PERSONAL");when(db.one("SELECT id,role,account_type FROM members WHERE id=? AND deleted_at IS NULL FOR UPDATE",1L)).thenReturn(Map.of("id",1L,"role","SUPER_ADMIN","accountType","PERSONAL"));
        assertThrows(ResponseStatusException.class,()->controller.deleteAccount(1L,()->"root"));
        when(db.one("SELECT id,role,account_type FROM members WHERE id=? AND deleted_at IS NULL FOR UPDATE",2L)).thenReturn(Map.of("id",2L,"role","SUPER_ADMIN","accountType","PERSONAL"));
        assertThrows(ResponseStatusException.class,()->controller.deleteAccount(2L,()->"root"));verify(db,never()).update(anyString(),any(Object[].class));
    }
}
