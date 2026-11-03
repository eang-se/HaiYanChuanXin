package cn.haiyan.platform.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ActivityDeletionTest {
    private final CommunityStore db=mock(CommunityStore.class);
    private final CommunityService service=new CommunityService(db,mock(WorkCollections.class),mock(AccountPermissions.class),new PenNames(db));
    @Test void deletionRemovesOnlyActivityAssociationsAndPreservesArticles(){
        when(db.one("SELECT id FROM tags WHERE id=? AND kind='TOPIC' AND is_activity=TRUE FOR UPDATE",5L)).thenReturn(Map.of("id",5L));
        service.deleteActivity(5L);
        var order=inOrder(db);order.verify(db).one("SELECT id FROM tags WHERE id=? AND kind='TOPIC' AND is_activity=TRUE FOR UPDATE",5L);order.verify(db).update("DELETE FROM work_tags WHERE tag_id=?",5L);order.verify(db).update("DELETE FROM tags WHERE id=? AND kind='TOPIC' AND is_activity=TRUE",5L);
        verify(db,never()).update(startsWith("DELETE FROM works"),any(Object[].class));
    }
    @Test void unknownAndContentTagIdsCannotBeDeletedAsActivities(){
        when(db.one("SELECT id FROM tags WHERE id=? AND kind='TOPIC' AND is_activity=TRUE FOR UPDATE",99L)).thenReturn(null);
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.deleteActivity(99L)).getStatusCode().value());verify(db,never()).update(anyString(),any(Object[].class));
    }
}
