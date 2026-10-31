package cn.haiyan.platform.service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkNotesTest {
 private final CommunityStore db=mock(CommunityStore.class);
 private final CommunityService service=spy(new CommunityService(db,mock(WorkCollections.class),mock(AccountPermissions.class),new PenNames(db)));
 private final AtomicReference<Object[]> saved=new AtomicReference<>();
 private void prepare(){
  doReturn(29L).when(service).memberId("editor");
  when(db.count("SELECT COUNT(*) FROM works WHERE id=? AND author_id=?",55L,29L)).thenReturn(1L);
  when(db.one("SELECT byline,source_note,notes,genre,custom_genre,body_layout,pen_name_id,collection_id FROM works WHERE id=?",55L)).thenReturn(Map.of("byline","胡杰","sourceNote","","notes","已有附注"));
  when(db.update(anyString(),any(Object[].class))).thenAnswer(i->{if(((String)i.getArgument(0)).startsWith("UPDATE works"))saved.set(i.getArguments());return 1;});
 }
 @Test void savesMultilineNotesSeparatelyFromBodyAndCanClearThem(){
  prepare();String notes="联合署名说明\n\n来源：原刊第2页";
  service.save(55L,"editor",new CommunityService.WorkInput("诗","诗歌","","<p>正文</p>","",List.of(),List.of(),false,"胡杰","",notes));
  var args=saved.get();assertEquals(notes,args[args.length-2]);assertFalse(((String)args[4]).contains("联合署名"));
  service.save(55L,"editor",new CommunityService.WorkInput("诗","诗歌","","<p>正文</p>","",List.of(),List.of(),false,"胡杰","",""));
  assertEquals("",saved.get()[saved.get().length-2]);
 }
 @Test void legacySourceIsMergedIntoNotesWithoutDuplicatingIt(){
  prepare();String notes="编辑说明\n\n来源：原刊";
  service.save(55L,"editor",new CommunityService.WorkInput("诗","诗歌","","<p>正文</p>","",List.of(),List.of(),false,"胡杰","来源：原刊",notes));
  var args=saved.get();assertEquals("",args[args.length-3]);assertEquals(notes,args[args.length-2]);
 }
 @Test void longNotesAreRejectedBeforeUpdating(){
  prepare();var e=assertThrows(ResponseStatusException.class,()->service.save(55L,"editor",new CommunityService.WorkInput("诗","诗歌","","<p>正文</p>","",List.of(),List.of(),false,"胡杰","","字".repeat(4001))));
  assertEquals(400,e.getStatusCode().value());assertNull(saved.get());
 }
}
