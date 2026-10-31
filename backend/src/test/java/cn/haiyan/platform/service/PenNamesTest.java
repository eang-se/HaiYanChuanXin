package cn.haiyan.platform.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class PenNamesTest {
 private final CommunityStore db=mock(CommunityStore.class);
 private final PenNames pens=new PenNames(db);
 @Test void olderClientsRetainBoundPenAndExplicitZeroClearsIt(){
  var pen=Map.<String,Object>of("id",2L,"name","胡杰");
  when(db.one("SELECT p.id,p.name FROM pen_names p JOIN member_pen_names b ON b.pen_name_id=p.id WHERE b.member_id=? AND p.id=?",29L,2L)).thenReturn(pen);
  assertEquals(pen,pens.resolve(29L,null,Map.of("penNameId",2L)));
  assertNull(pens.resolve(29L,0L,Map.of("penNameId",2L)));
 }
 @Test void unboundPenCannotBeUsedForAnArticle(){
  when(db.one("SELECT p.id,p.name FROM pen_names p JOIN member_pen_names b ON b.pen_name_id=p.id WHERE b.member_id=? AND p.id=?",29L,2L)).thenReturn(null);
  assertEquals(403,assertThrows(ResponseStatusException.class,()->pens.resolve(29L,2L,null)).getStatusCode().value());
 }
 @Test void aliasesStillUsedByAnAccountCannotBeUnbound(){
  when(db.count("SELECT COUNT(*) FROM works WHERE author_id=? AND pen_name_id=?",29L,2L)).thenReturn(1L);
  assertEquals(400,assertThrows(ResponseStatusException.class,()->pens.unbind(29L,2L)).getStatusCode().value());
  verify(db,never()).update(anyString(),any(Object[].class));
 }
 @Test void pendingSignatureIsNotAnIdentityAndBindingLimitIsEnforced(){
  assertThrows(ResponseStatusException.class,()->pens.bind(29L,"署名待核"));
  when(db.one("SELECT p.id FROM pen_names p JOIN member_pen_names b ON b.pen_name_id=p.id WHERE b.member_id=? AND p.name=?",29L,"新笔名")).thenReturn(null);
  when(db.count("SELECT COUNT(*) FROM member_pen_names WHERE member_id=?",29L)).thenReturn(30L);
  assertThrows(ResponseStatusException.class,()->pens.bind(29L,"新笔名"));
  verify(db,never()).update(anyString(),any(Object[].class));
 }
}
