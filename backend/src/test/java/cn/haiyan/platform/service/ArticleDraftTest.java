package cn.haiyan.platform.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleDraftTest {
    private final CommunityStore db=mock(CommunityStore.class);
    private final CommunityService service=spy(new CommunityService(db,mock(WorkCollections.class),mock(AccountPermissions.class),new PenNames(db)));
    private void setup(){doReturn(29L).when(service).memberId("editor");when(db.count("SELECT COUNT(*) FROM works WHERE id=? AND author_id=?",55L,29L)).thenReturn(1L);when(db.one("SELECT status,edit_revision FROM works WHERE id=? FOR UPDATE",55L)).thenReturn(Map.of("status","PUBLISHED","editRevision",2L));}
    private CommunityService.WorkInput input(long rev,boolean publish) {
        return new CommunityService.WorkInput("私密标题","散文","","<p>私密正文</p>","",List.of(),List.of(),publish,"作者","","","",null,0L,0L,Map.of("type","doc","content",List.of(Map.of("type","paragraph","content",List.of(Map.of("type","text","text","私密正文"))))),rev);
    }
    @Test void savingPublishedDraftDoesNotMutatePublicContentOrMedia(){
        setup();var result=service.save(55L,"editor",input(2,false));
        assertEquals("PUBLISHED",result.get("status"));assertEquals(3L,result.get("draftRevision"));
        verify(db,never()).update(startsWith("UPDATE works SET title="),any(Object[].class));
        verify(db,never()).update(eq("DELETE FROM work_media WHERE work_id=?"),any(Object[].class));
        verify(db).update(eq("UPDATE works SET edit_revision=edit_revision+1,updated_at=updated_at WHERE id=?"),eq(55L));
        verify(db).update(startsWith("INSERT INTO work_drafts"),any(Object[].class));
    }
    @Test void staleDraftAndStalePublicationAreRejected(){
        setup();for(boolean publish:List.of(false,true))assertEquals(409,assertThrows(ResponseStatusException.class,()->service.save(55L,"editor",input(1,publish))).getStatusCode().value());
        verify(db,never()).update(startsWith("UPDATE works SET title="),any(Object[].class));
    }
    @Test void anotherUserCannotReadVersions(){
        doReturn(12L).when(service).memberId("other");
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.versions(55L,"other")).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.version(55L,4L,"other")).getStatusCode().value());
    }
}
