package cn.haiyan.platform.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DraftDeletionTest {
    private final CommunityStore db=mock(CommunityStore.class);
    private final AccountPermissions permissions=mock(AccountPermissions.class);
    private final CommunityService service=spy(new CommunityService(db,mock(WorkCollections.class),permissions,mock(PenNames.class)));
    private void prepare(String status){
        doReturn(29L).when(service).memberId("author");
        when(db.one("SELECT status FROM works WHERE id=? AND author_id=? FOR UPDATE",55L,29L)).thenReturn(status==null?null:Map.of("status",status));
        when(db.update("DELETE FROM works WHERE id=? AND author_id=? AND status IN ('DRAFT','WITHDRAWN')",55L,29L)).thenReturn(1);
    }
    @Test void ownedDraftIsDeletedAfterItsRelationsAndMediaFilesAreRetained(){
        prepare("DRAFT");service.deleteDraft(55L,"author");
        var order=inOrder(permissions,db);
        order.verify(permissions).require("author","WRITE");order.verify(db).one("SELECT id FROM members WHERE id=? FOR UPDATE",29L);
        order.verify(db).one("SELECT status FROM works WHERE id=? AND author_id=? FOR UPDATE",55L,29L);
        for(String table:List.of("work_tags","work_media","collection_works","reactions","comments"))order.verify(db).update("DELETE FROM "+table+" WHERE work_id=?",55L);
        order.verify(db).update("DELETE FROM works WHERE id=? AND author_id=? AND status IN ('DRAFT','WITHDRAWN')",55L,29L);
        order.verifyNoMoreInteractions();
    }
    @Test void withdrawnDraftBoxItemsCanAlsoBeDeleted(){prepare("WITHDRAWN");service.deleteDraft(55L,"author");verify(db).update(startsWith("DELETE FROM works WHERE"),eq(55L),eq(29L));}
    @Test void missingAndOtherAuthorsWorksAreNotDeleted(){
        prepare(null);var error=assertThrows(ResponseStatusException.class,()->service.deleteDraft(55L,"author"));
        assertEquals(404,error.getStatusCode().value());verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void publishedWorksAreProtectedEvenIfTheClientShowsAStaleDraft(){
        prepare("PUBLISHED");var error=assertThrows(ResponseStatusException.class,()->service.deleteDraft(55L,"author"));
        assertEquals(409,error.getStatusCode().value());verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void writePermissionIsRequiredBeforeAnyDeletion(){
        prepare("DRAFT");doThrow(CommunityService.error(403,"未获权限")).when(permissions).require("author","WRITE");
        assertThrows(ResponseStatusException.class,()->service.deleteDraft(55L,"author"));verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void unexpectedDeleteCountFailsTheTransaction(){
        prepare("DRAFT");when(db.update("DELETE FROM works WHERE id=? AND author_id=? AND status IN ('DRAFT','WITHDRAWN')",55L,29L)).thenReturn(0);
        var error=assertThrows(ResponseStatusException.class,()->service.deleteDraft(55L,"author"));assertEquals(409,error.getStatusCode().value());
    }
}
