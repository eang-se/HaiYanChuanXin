package cn.haiyan.platform.service;

import java.util.*;
import cn.haiyan.platform.web.CollectionController;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollectionDeletionTest {
 private final CommunityStore db=mock(CommunityStore.class);
 private final AccountPermissions permissions=mock(AccountPermissions.class);
 private final CommunityService service=spy(new CommunityService(db,mock(WorkCollections.class),permissions,new PenNames(db)));
 private final CollectionController controller=new CollectionController(service,permissions);
 private final String affected="SELECT w.id FROM works w LEFT JOIN work_drafts d ON d.work_id=w.id WHERE w.author_id=? AND (w.collection_id=? OR JSON_UNQUOTE(JSON_EXTRACT(d.payload,'$.collectionId'))=?) FOR UPDATE";
 @Test void deletionKeepsWorksAndClearsPublishedAndDraftReferences(){
  doReturn(29L).when(service).memberId("editor");when(db.one("SELECT id FROM collections WHERE id=? AND author_id=? FOR UPDATE",10L,29L)).thenReturn(Map.of("id",10L));
  when(db.list(affected,29L,10L,"10")).thenReturn(List.of(Map.of("id",55L),Map.of("id",56L)));
  controller.delete(10L,()->"editor");
  verify(permissions).require("editor","COLLECTIONS");
  for(long id:List.of(55L,56L)){
   verify(db).update("UPDATE works SET edit_revision=edit_revision+1,updated_at=updated_at WHERE id=? AND author_id=?",id,29L);
   verify(db).update("UPDATE work_drafts SET payload=JSON_SET(payload,'$.collectionId',0),revision=revision+1 WHERE work_id=? AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.collectionId'))=?",id,"10");
   verify(db).update("UPDATE article_reviews SET status='SUPERSEDED',decided_at=CURRENT_TIMESTAMP WHERE work_id=? AND status='PENDING'",id);
  }
  verify(db).update("UPDATE works SET collection_id=NULL,collection_position=NULL,updated_at=updated_at WHERE collection_id=? AND author_id=?",10L,29L);
  verify(db).update("DELETE FROM collection_works WHERE collection_id=?",10L);
  verify(db).update("DELETE FROM collections WHERE id=? AND author_id=?",10L,29L);
  verify(db,never()).update(startsWith("DELETE FROM works"),any(Object[].class));
 }
 @Test void deletionRequiresCollectionPermission(){
  doReturn(29L).when(service).memberId("editor");
  doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(permissions).require("editor","COLLECTIONS");
  assertEquals(403,assertThrows(ResponseStatusException.class,()->controller.delete(10L,()->"editor")).getStatusCode().value());
  verifyNoInteractions(db);
 }
 @Test void cannotDeleteAnotherAuthorsCollection(){
  doReturn(29L).when(service).memberId("editor");when(db.one("SELECT id FROM collections WHERE id=? AND author_id=? FOR UPDATE",10L,29L)).thenReturn(null);
  assertEquals(404,assertThrows(ResponseStatusException.class,()->controller.delete(10L,()->"editor")).getStatusCode().value());
  verify(db,never()).update(anyString(),any(Object[].class));
 }
}
