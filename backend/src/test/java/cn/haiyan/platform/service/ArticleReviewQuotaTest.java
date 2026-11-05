package cn.haiyan.platform.service;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ArticleReviewQuotaTest {
 private final CommunityService community=mock(CommunityService.class);
 private final CommunityStore db=mock(CommunityStore.class);
 private final ArticleReviewQuota quota=new ArticleReviewQuota(community);
 private final CommunityService.AiSnapshot snapshot=new CommunityService.AiSnapshot("<p>正文</p>",Map.of("title","标题"),"");
 @BeforeEach void setup(){when(community.store()).thenReturn(db);when(community.member("member")).thenReturn(Map.of("id",5L,"accountType","PERSONAL"));}
 @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
 @Test void firstAutomaticReviewOfWholeArticleConsumesOneAttempt(){
  when(db.one(startsWith("SELECT attempts"),eq(5L),any(LocalDate.class))).thenReturn(Map.of("attempts",9));
  quota.reserve("member",4L,snapshot,false);
  verify(db).update(startsWith("UPDATE article_review_daily_quota SET attempts="),eq(5L),any(LocalDate.class));
  verify(db).insert(startsWith("INSERT INTO article_review_quota_receipts"),eq(5L),any(LocalDate.class),anyString());
 }
 @Test void sameSnapshotManualReviewReusesAutomaticAttemptEvenAtLimit(){
  when(db.one(startsWith("SELECT attempts"),eq(5L),any(LocalDate.class))).thenReturn(Map.of("attempts",10));
  when(db.one(startsWith("SELECT id FROM article_review_quota_receipts"),eq(5L),any(LocalDate.class),anyString())).thenReturn(Map.of("id",8L));
  quota.reserve("member",4L,snapshot,true);
  verify(db).update("UPDATE article_review_quota_receipts SET manual_claimed=TRUE WHERE id=?",8L);
  verify(db,never()).update(startsWith("UPDATE article_review_daily_quota SET attempts="),any(),any());
 }
 @Test void exceedingLimitRejectsNewSubmissions(){
  when(db.one(startsWith("SELECT attempts"),eq(5L),any(LocalDate.class))).thenReturn(Map.of("attempts",10));
  when(db.one(startsWith("SELECT id FROM article_review_quota_receipts"),eq(5L),any(LocalDate.class),anyString())).thenReturn(null);
  assertEquals(429,assertThrows(ResponseStatusException.class,()->quota.reserve("member",4L,snapshot,false)).getStatusCode().value());
  assertEquals(429,assertThrows(ResponseStatusException.class,()->quota.reserve("member",4L,snapshot,true)).getStatusCode().value());
 }
 @Test void publicIdentitiesAndPersonalAdminsAreExempt(){
  when(community.member("public")).thenReturn(Map.of("id",6L,"accountType","PUBLIC"));quota.reserve("public",null,snapshot,false);
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("member",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));quota.reserve("member",4L,snapshot,false);
  verifyNoInteractions(db);
 }
 @Test void changedTextOrCoverCannotReusePriorReceipt(){
  var changed=new CommunityService.AiSnapshot(snapshot.body(),snapshot.fields(),"/api/media/12345678-1234-1234-1234-123456789abc");
  assertNotEquals(ArticleReviewQuota.fingerprint(4L,snapshot),ArticleReviewQuota.fingerprint(4L,changed));
  assertNotEquals(ArticleReviewQuota.fingerprint(4L,snapshot),ArticleReviewQuota.fingerprint(5L,snapshot));
 }
}
