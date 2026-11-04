package cn.haiyan.platform.service;

import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ArticleManagementTest {
    final CommunityStore db=mock(CommunityStore.class);
    final CommunityService service=new CommunityService(db,mock(WorkCollections.class),mock(AccountPermissions.class),mock(PenNames.class));
    void role(String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("selected",null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
    void work(String status){when(db.one("SELECT author_id FROM works WHERE id=?",55L)).thenReturn(Map.of("authorId",29L));when(db.one("SELECT * FROM works WHERE id=? FOR UPDATE",55L)).thenReturn(Map.of("authorId",29L,"status",status,"title","来信","body","<p>原有公开正文</p>"));}
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    @Test void publicStaffCanReviewButCannotDelete(){
        role("PUBLIC_STAFF");work("WITHDRAWN");service.reviewArticle(55L,true);
        verify(db).update("UPDATE works SET status='PUBLISHED',published_at=COALESCE(published_at,CURRENT_TIMESTAMP),edit_revision=edit_revision+1 WHERE id=?",55L);
        verify(db,never()).one(contains("work_drafts"),any(Object[].class));
        assertThrows(ResponseStatusException.class,()->service.deleteArticle(55L));
    }
    @Test void orphanedWorkRequiresPublicIdentityAndIsClaimedWhenRepublished(){
        var owner=new HashMap<String,Object>();owner.put("authorId",null);
        var orphan=new HashMap<String,Object>(Map.of("status","WITHDRAWN","title","来信","body","<p>原有正文</p>"));orphan.put("authorId",null);
        when(db.one("SELECT author_id FROM works WHERE id=?",55L)).thenReturn(owner);
        when(db.one("SELECT * FROM works WHERE id=? FOR UPDATE",55L)).thenReturn(orphan);
        role("ADMIN");assertThrows(ResponseStatusException.class,()->service.reviewArticle(55L,true));
        when(db.one("SELECT id FROM members WHERE username=? AND account_type='PUBLIC' AND deleted_at IS NULL","selected")).thenReturn(Map.of("id",77L));
        when(db.update("UPDATE works SET author_id=? WHERE id=? AND author_id IS NULL",77L,55L)).thenReturn(1);
        role("PUBLIC_STAFF");service.reviewArticle(55L,true);
        verify(db).update("UPDATE works SET author_id=? WHERE id=? AND author_id IS NULL",77L,55L);
        verify(db).update("UPDATE works SET status='PUBLISHED',published_at=COALESCE(published_at,CURRENT_TIMESTAMP),edit_revision=edit_revision+1 WHERE id=?",55L);
    }
    @Test void administratorsCannotPublishAnAuthorsHiddenArticle(){
        role("ADMIN");work("UNPUBLISHED");assertThrows(ResponseStatusException.class,()->service.reviewArticle(55L,true));
        verify(db,never()).update(startsWith("UPDATE works SET status='PUBLISHED'"),any(Object[].class));
    }
    @Test void deletedAuthorsArticlesCannotBeRepublished(){
        role("ADMIN");work("WITHDRAWN");when(db.count("SELECT COUNT(*) FROM members WHERE id=? AND deleted_at IS NOT NULL",29L)).thenReturn(1L);
        assertThrows(ResponseStatusException.class,()->service.reviewArticle(55L,true));
        verify(db,never()).update(startsWith("UPDATE works SET status='PUBLISHED'"),any(Object[].class));
    }
    @Test void administratorDeletionCleansVersionMediaAndRelationsBeforeDeletingWork(){
        role("ADMIN");work("PUBLISHED");when(db.update("DELETE FROM works WHERE id=? AND status<>'DRAFT'",55L)).thenReturn(1);
        service.deleteArticle(55L);var order=inOrder(db);
        order.verify(db).update("DELETE vm FROM work_version_media vm JOIN work_versions v ON v.id=vm.version_id WHERE v.work_id=?",55L);
        order.verify(db).update("DELETE FROM work_tags WHERE work_id=?",55L);
        order.verify(db).update("DELETE FROM work_media WHERE work_id=?",55L);
        order.verify(db).update("DELETE FROM collection_works WHERE work_id=?",55L);
        order.verify(db).update("DELETE FROM reactions WHERE work_id=?",55L);
        order.verify(db).update("DELETE FROM comments WHERE work_id=?",55L);
        order.verify(db).update("DELETE FROM works WHERE id=? AND status<>'DRAFT'",55L);
    }
    @Test void privateDraftsCannotBeReviewedOrDeletedThroughAdministration(){
        role("ADMIN");work("DRAFT");assertThrows(ResponseStatusException.class,()->service.reviewArticle(55L,true));assertThrows(ResponseStatusException.class,()->service.deleteArticle(55L));verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void ordinaryPersonalMembersCannotReview(){role("MEMBER");assertThrows(ResponseStatusException.class,()->service.reviewArticle(55L,true));verifyNoInteractions(db);}
}
