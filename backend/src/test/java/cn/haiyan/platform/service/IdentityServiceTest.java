package cn.haiyan.platform.service;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityServiceTest {
 private final CommunityStore db=mock(CommunityStore.class);
 private final IdentityService service=new IdentityService(db);
 private final MockHttpSession session=new MockHttpSession();
 private static final String TARGET="SELECT id,username,account_type FROM members WHERE id=? FOR UPDATE";
 private static final String AUTH="SELECT member_id FROM public_account_members WHERE public_member_id=? AND member_id=? FOR UPDATE";
 private static final String LEASES="SELECT actor_id FROM public_account_sessions WHERE public_member_id=? FOR UPDATE";
 private void publicTarget(){when(db.one(TARGET,10L)).thenReturn(Map.of("id",10L,"username","public_name","accountType","PUBLIC"));}
 @Test void canSelectWhitelistedPublicIdentity(){
  publicTarget();when(db.one(AUTH,10L,1L)).thenReturn(Map.of("memberId",1L));when(db.list(LEASES,10L)).thenReturn(List.of());
  assertEquals("public_name",service.select(session,1L,10L));assertEquals(10L,session.getAttribute(IdentityService.SELECTED));
  verify(db).update(startsWith("INSERT INTO public_account_sessions"),eq(10L),eq(1L),anyString());
 }
 @Test void deniesUnknownOrUnlistedIdentity(){
  publicTarget();when(db.one(AUTH,10L,1L)).thenReturn(null);
  assertEquals(403,assertThrows(ResponseStatusException.class,()->service.select(session,1L,10L)).getStatusCode().value());
  verify(db,never()).update(startsWith("INSERT INTO public_account_sessions"),any(),any(),any());
 }
 @Test void sixthDistinctPersonIsRejectedButExistingPersonMayUseAnotherSession(){
  publicTarget();when(db.one(AUTH,10L,1L)).thenReturn(Map.of("memberId",1L));when(db.one(AUTH,10L,6L)).thenReturn(Map.of("memberId",6L));
  when(db.list(LEASES,10L)).thenReturn(List.of(Map.of("actorId",1L),Map.of("actorId",2L),Map.of("actorId",3L),Map.of("actorId",4L),Map.of("actorId",5L)));
  assertEquals(409,assertThrows(ResponseStatusException.class,()->service.select(session,6L,10L)).getStatusCode().value());
  assertEquals("public_name",service.select(session,1L,10L));
 }
 @Test void revokedWhitelistReturnsToPersonalAndDropsLease(){
  session.setAttribute(IdentityService.SELECTED,10L);
  when(db.one("SELECT m.username FROM members m JOIN public_account_members p ON p.public_member_id=m.id WHERE m.id=? AND m.account_type='PUBLIC' AND p.member_id=?",10L,1L)).thenReturn(null);
  assertEquals("alice",service.current(session,1L,"alice"));assertNull(session.getAttribute(IdentityService.SELECTED));
  verify(db).update(eq("DELETE FROM public_account_sessions WHERE session_hash=?"),anyString());
 }
 @Test void expiredLeaseReturnsToPersonal(){
  session.setAttribute(IdentityService.SELECTED,10L);
  when(db.one("SELECT m.username FROM members m JOIN public_account_members p ON p.public_member_id=m.id WHERE m.id=? AND m.account_type='PUBLIC' AND p.member_id=?",10L,1L)).thenReturn(Map.of("username","public_name"));
  assertEquals("alice",service.current(session,1L,"alice"));assertNull(session.getAttribute(IdentityService.SELECTED));
 }
 @Test void cannotSelectAnotherPersonalAccount(){
  when(db.one(TARGET,2L)).thenReturn(Map.of("id",2L,"username","other","accountType","PERSONAL"));
  assertEquals(403,assertThrows(ResponseStatusException.class,()->service.select(session,1L,2L)).getStatusCode().value());
 }
}
