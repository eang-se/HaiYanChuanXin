package cn.haiyan.platform.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class WorkCollectionsTest {
 private final CommunityStore db=mock(CommunityStore.class);
 private final WorkCollections collections=new WorkCollections(db);
 @Test void omittedCollectionIsPreservedAndClearingRemovesPosition(){
  collections.assign(22,29,null);verifyNoInteractions(db);
  when(db.one("SELECT collection_id FROM works WHERE id=? AND author_id=? FOR UPDATE",22L,29L)).thenReturn(Map.of("collectionId",9L));
  collections.assign(22,29,0L);verify(db).update("UPDATE works SET collection_id=NULL,collection_position=NULL WHERE id=?",22L);
 }
 @Test void movingWorkReplacesItsOnlyCollectionAndAppendingKeepsOrder(){
  when(db.one("SELECT collection_id FROM works WHERE id=? AND author_id=? FOR UPDATE",22L,29L)).thenReturn(Map.of("collectionId",9L));
  when(db.one("SELECT id FROM collections WHERE id=? AND author_id=? FOR UPDATE",10L,29L)).thenReturn(Map.of("id",10L));
  when(db.count("SELECT COALESCE(MAX(collection_position),-1)+1 FROM works WHERE collection_id=?",10L)).thenReturn(7L);
  collections.assign(22,29,10L);verify(db).update("UPDATE works SET collection_id=?,collection_position=? WHERE id=?",10L,7L,22L);
 }
 @Test void sameCollectionPreservesManualPositionEvenWhenFull(){
  when(db.one("SELECT collection_id FROM works WHERE id=? AND author_id=? FOR UPDATE",22L,29L)).thenReturn(Map.of("collectionId",9L));
  when(db.one("SELECT id FROM collections WHERE id=? AND author_id=? FOR UPDATE",9L,29L)).thenReturn(Map.of("id",9L));
  collections.assign(22,29,9L);verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void otherAccountsCollectionsAndFullCollectionsAreRejected(){
  when(db.one("SELECT id FROM collections WHERE id=? AND author_id=? FOR UPDATE",9L,29L)).thenReturn(null);
  when(db.one("SELECT collection_id FROM works WHERE id=? AND author_id=? FOR UPDATE",22L,29L)).thenReturn(new HashMap<>());
  assertEquals(403,assertThrows(ResponseStatusException.class,()->collections.assign(22,29,9L)).getStatusCode().value());
  when(db.one("SELECT id FROM collections WHERE id=? AND author_id=? FOR UPDATE",9L,29L)).thenReturn(Map.of("id",9L));
  when(db.count("SELECT COUNT(*) FROM works WHERE collection_id=?",9L)).thenReturn(500L);
  assertEquals(400,assertThrows(ResponseStatusException.class,()->collections.assign(22,29,9L)).getStatusCode().value());
  verify(db,never()).update(anyString(),any(Object[].class));
 }
}
