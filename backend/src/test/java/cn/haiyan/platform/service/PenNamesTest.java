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
 @Test void availabilityDistinguishesOwnOtherAndUnclaimedNamesWithoutLeakingOwners(){
  assertEquals("AVAILABLE",pens.availability(29L,"新笔名").get("status"));
  when(db.one("SELECT id,name FROM pen_names WHERE name=?","林间")).thenReturn(Map.of("id",3L,"name","林间"));
  when(db.count("SELECT COUNT(*) FROM member_pen_names WHERE pen_name_id=? AND member_id<>?",3L,29L)).thenReturn(1L);
  assertEquals(Map.of("status","OTHER"),pens.availability(29L,"林间"));
  when(db.count("SELECT COUNT(*) FROM member_pen_names WHERE member_id=? AND pen_name_id=?",29L,3L)).thenReturn(1L);
  assertEquals("SELF",pens.availability(29L,"林间").get("status"));
 }
 @Test void bindingRejectsANameClaimedAfterTheAvailabilityCheck(){
  when(db.one("SELECT p.id FROM pen_names p JOIN member_pen_names b ON b.pen_name_id=p.id WHERE b.member_id=? AND p.name=?",29L,"林间")).thenReturn(null);
  when(db.one("SELECT id,name FROM pen_names WHERE name=? FOR UPDATE","林间")).thenReturn(Map.of("id",3L,"name","林间"));
  when(db.one("SELECT member_id FROM member_pen_names WHERE pen_name_id=? AND member_id<>? LIMIT 1 FOR UPDATE",3L,29L)).thenReturn(Map.of("memberId",99L));
  assertEquals(409,assertThrows(ResponseStatusException.class,()->pens.bind(29L,"林间")).getStatusCode().value());
  verify(db,never()).update("INSERT IGNORE INTO member_pen_names(member_id,pen_name_id) VALUES(?,?)",29L,3L);
 }
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
 @Test void bindingLimitAndLengthAreEnforcedAtCharacterBoundaries(){
  assertThrows(ResponseStatusException.class,()->pens.availability(29L,"中".repeat(11)));
  assertThrows(ResponseStatusException.class,()->pens.bind(29L,"😀".repeat(11)));
  assertEquals("AVAILABLE",pens.availability(29L,"😀".repeat(10)).get("status"));
  when(db.one("SELECT p.id FROM pen_names p JOIN member_pen_names b ON b.pen_name_id=p.id WHERE b.member_id=? AND p.name=?",29L,"新笔名")).thenReturn(null);
  when(db.count("SELECT COUNT(*) FROM member_pen_names WHERE member_id=?",29L)).thenReturn(3L);
  assertThrows(ResponseStatusException.class,()->pens.bind(29L,"新笔名"));
  verify(db,never()).update(startsWith("INSERT INTO pen_names"),any(Object[].class));
 }
}
