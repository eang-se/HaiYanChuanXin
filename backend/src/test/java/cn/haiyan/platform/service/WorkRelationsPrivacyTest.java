package cn.haiyan.platform.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import cn.haiyan.platform.web.CollectionController;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class WorkRelationsPrivacyTest {
 private final CommunityStore db=mock(CommunityStore.class);
 private final CommunityService service=spy(new CommunityService(db,mock(WorkCollections.class),mock(AccountPermissions.class),new PenNames(db)));
 @Test void publicArticlesDoNotExposePrivateCollectionMetadata(){
  var work=new HashMap<String,Object>(Map.of("id",22L,"authorId",29L,"plainText","正文","genre","诗歌","collectionId",42L,"collectionPosition",7));
  when(db.one("SELECT id,title,kind,published FROM collections WHERE id=? AND published=TRUE",42L)).thenReturn(null);
  service.decorate(List.of(work),false,null);
  assertNull(work.get("collection"));assertNull(work.get("collectionId"));assertNull(work.get("collectionPosition"));assertEquals(29L,work.get("authorId"));
 }
 @Test void collectionEditorCannotSilentlyTakeAnArticleFromAnotherCollection(){
  doReturn(29L).when(service).memberId("editor");
  var controller=new CollectionController(service,mock(AccountPermissions.class));
  when(db.one("SELECT id,collection_id FROM works WHERE id=? AND author_id=? FOR UPDATE",22L,29L)).thenReturn(Map.of("id",22L,"collectionId",9L));
  var e=assertThrows(ResponseStatusException.class,()->controller.create(()->"editor",new CollectionController.Input("新合集","","选集",false,List.of(22L))));
  assertEquals(400,e.getStatusCode().value());verify(db,never()).insert(anyString(),any(Object[].class));
 }
 @Test void namingAnotherAccountAsWriterDoesNotGrantOwnership(){
  doReturn(29L).when(service).memberId("editor");
  var input=new CommunityService.WorkInput("作品","散文","","<p>正文</p>","",List.of(),List.of(),false,"他人笔名","","",null,null,9L,2L);
  assertEquals(404,assertThrows(ResponseStatusException.class,()->service.save(88L,"editor",input)).getStatusCode().value());
  verify(db,never()).update(anyString(),any(Object[].class));
 }
}
