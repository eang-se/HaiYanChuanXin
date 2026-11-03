package cn.haiyan.platform.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import cn.haiyan.platform.web.CollectionController;
import static org.mockito.Mockito.*;

class CollectionDraftRevisionTest {
 @Test void collectionChangeInvalidatesOpenEditorsAndSynchronizesUnchangedDraftSelection(){
  var db=mock(CommunityStore.class);var s=spy(new CommunityService(db,mock(WorkCollections.class),mock(AccountPermissions.class),new PenNames(db)));
  doReturn(29L).when(s).memberId("editor");
  when(db.count("SELECT COUNT(*) FROM collections WHERE id=? AND author_id=?",10L,29L)).thenReturn(1L);
  when(db.list("SELECT id FROM works WHERE collection_id=? AND author_id=?",10L,29L)).thenReturn(List.of(Map.of("id",55L)));
  when(db.one("SELECT id,collection_id FROM works WHERE id=? AND author_id=? FOR UPDATE",56L,29L)).thenReturn(Map.of("id",56L));
  new CollectionController(s,mock(AccountPermissions.class)).update(10L,()->"editor",new CollectionController.Input("合集","","诗集",true,List.of(56L)));
  verify(db).update("UPDATE works SET edit_revision=edit_revision+1,updated_at=updated_at WHERE id=?",55L);
  verify(db).update("UPDATE works SET edit_revision=edit_revision+1,updated_at=updated_at WHERE id=?",56L);
  String sql="UPDATE work_drafts SET payload=JSON_SET(payload,'$.collectionId',?),revision=revision+1 WHERE work_id=? AND COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(payload,'$.collectionId')),'null'),'0')=?";
  verify(db).update(sql,0L,55L,"10");verify(db).update(sql,10L,56L,"0");
 }
}
