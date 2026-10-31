package cn.haiyan.platform.service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkBylineTest {
 private final CommunityStore db=mock(CommunityStore.class);
 private final WorkCollections tags=mock(WorkCollections.class);
 private final AccountPermissions permissions=mock(AccountPermissions.class);
 private final CommunityService service=spy(new CommunityService(db,tags,permissions,new PenNames(db)));
 private void prepare(){doReturn(29L).when(service).memberId("editor");}
 @Test void creditedWriterDoesNotChangeUploadingAccountAndHtmlIsStillCleaned(){
  prepare();var saved=new AtomicReference<Object[]>();
  when(db.insert(anyString(),any(Object[].class))).thenAnswer(i->{saved.set(i.getArguments());return 55L;});
  service.save(null,"editor",new CommunityService.WorkInput("诗","诗歌","","<p>一行</p><script>alert(1)</script>","",List.of(),List.of(),false,"胡杰","原刊第2页"));
  var args=saved.get();assertNotNull(args);assertEquals(29L,args[1]);
  assertFalse(((String)args[5]).contains("script"));
  assertEquals("胡杰",args[args.length-3]);assertEquals("",args[args.length-2]);assertEquals("原刊第2页",args[args.length-1]);
 }
 @Test void olderClientEditingDoesNotEraseExistingAttribution(){
  prepare();when(db.count("SELECT COUNT(*) FROM works WHERE id=? AND author_id=?",55L,29L)).thenReturn(1L);
  when(db.one("SELECT byline,source_note,notes,genre,custom_genre,body_layout,pen_name_id,collection_id FROM works WHERE id=?",55L)).thenReturn(Map.of("byline","胡杰","sourceNote","","notes","原刊第2页"));
  var saved=new AtomicReference<Object[]>();
  when(db.update(anyString(),any(Object[].class))).thenAnswer(i->{if(((String)i.getArgument(0)).startsWith("UPDATE works"))saved.set(i.getArguments());return 1;});
  service.save(55L,"editor",new CommunityService.WorkInput("诗","诗歌","","<p>修订</p>","",List.of(),false));
  var args=saved.get();assertNotNull(args);assertEquals("胡杰",args[args.length-4]);assertEquals("原刊第2页",args[args.length-2]);
 }
 @Test void bylineCannotGrantEditingRightsToAnotherAccount(){
  prepare();when(db.count("SELECT COUNT(*) FROM works WHERE id=? AND author_id=?",55L,29L)).thenReturn(0L);
  var e=assertThrows(ResponseStatusException.class,()->service.save(55L,"editor",new CommunityService.WorkInput("诗","诗歌","","<p>修订</p>","",List.of(),List.of(),false,"editor","")));
  assertEquals(404,e.getStatusCode().value());verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void overlyLongBylineIsRejectedBeforeWriting(){
  prepare();var e=assertThrows(ResponseStatusException.class,()->service.save(null,"editor",new CommunityService.WorkInput("诗","诗歌","","<p>诗</p>","",List.of(),List.of(),false,"字".repeat(121),"")));
  assertEquals(400,e.getStatusCode().value());verify(db,never()).insert(anyString(),any(Object[].class));
 }
}
