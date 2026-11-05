package cn.haiyan.platform.service;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleReviewServiceTest {
 private final CommunityStore db=mock(CommunityStore.class);
 private final CommunityService community=mock(CommunityService.class);
 private final ArticleReviewQuota quota=mock(ArticleReviewQuota.class);
 private final ArticleReviewService reviews=new ArticleReviewService(community,quota);
 @BeforeEach void init(){when(community.store()).thenReturn(db);}
 @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
 private void role(String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reviewer",null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
 private CommunityService.WorkInput input(String text){return new CommunityService.WorkInput("标题","散文","","<p>"+text+"</p>","",List.of(),true);}
 @Test void openSourceRulesScreenTaggedWordsAndCommentProfanity(){
  assertTrue(ArticleModeration.check("河畔的风吹过校园").isEmpty());
  assertFalse(ArticleModeration.checkMatch("博彩").isEmpty());
  assertFalse(ArticleModeration.checkMatch("广告代理").isEmpty());
  assertTrue(ArticleModeration.checkArticleMatch("基督教").isEmpty());
  assertFalse(ArticleModeration.checkComment("你这个傻逼").isEmpty());
  assertTrue(ArticleModeration.checkComment("春天的校园很美").isEmpty());
 }
 @Test void submissionSavesDraftAndNeverPublishesAutomatically(){
  when(community.save(isNull(),eq("author"),any())).thenReturn(Map.of("id",7L,"draftRevision",1L));
  var result=reviews.submit(null,"author",input("这是博彩、博彩、博彩的新闻报道"));
  assertEquals("PENDING",result.get("status"));
  verify(community).save(isNull(),eq("author"),argThat(f->!f.publish()));
  verify(community,never()).saveReviewed(anyLong(),anyString(),any());
  verify(db).insert(startsWith("INSERT INTO article_reviews"),eq(7L),eq(1L),anyString(),contains("作者申请人工复核"));
 }
 @Test void voluntaryReviewCanBeRequestedWhenAutomaticReviewIsUnavailable(){when(community.save(isNull(),eq("author"),any())).thenReturn(Map.of("id",7L,"draftRevision",1L));assertEquals("PENDING",reviews.submit(null,"author",input("普通文章")).get("status"));verify(community,never()).saveReviewed(anyLong(),anyString(),any());}
 @Test void reviewersCannotApproveChangedOrAlreadyHandledVersions(){
  role("PUBLIC_STAFF");when(db.one("SELECT work_id FROM article_reviews WHERE id=?",4L)).thenReturn(Map.of("workId",7L));
  when(db.one(startsWith("SELECT w.author_id"),eq(7L))).thenReturn(Map.of("authorId",9L,"username","author"));
  when(db.one("SELECT edit_revision FROM works WHERE id=? FOR UPDATE",7L)).thenReturn(Map.of("editRevision",3L));
  when(db.one("SELECT * FROM article_reviews WHERE id=? FOR UPDATE",4L)).thenReturn(Map.of("status","PENDING","draftRevision",2L));
  assertEquals(409,assertThrows(ResponseStatusException.class,()->reviews.decide(4L,"reviewer",true,"")).getStatusCode().value());
  verify(community,never()).saveReviewed(anyLong(),anyString(),any());
 }
 @Test void approvalPublishesExactlyTheSubmittedRevision(){
  role("ADMIN");var submitted=input("关于赌博网站的报道");
  var json=new tools.jackson.databind.ObjectMapper().writeValueAsString(submitted);
  when(db.one("SELECT work_id FROM article_reviews WHERE id=?",4L)).thenReturn(Map.of("workId",7L));
  when(db.one(startsWith("SELECT w.author_id"),eq(7L))).thenReturn(Map.of("authorId",9L,"username","author"));
  when(db.one("SELECT edit_revision FROM works WHERE id=? FOR UPDATE",7L)).thenReturn(Map.of("editRevision",2L));
  when(db.one("SELECT * FROM article_reviews WHERE id=? FOR UPDATE",4L)).thenReturn(Map.of("status","PENDING","draftRevision",2L,"payload",json));
  when(community.memberId("reviewer")).thenReturn(10L);
  reviews.decide(4L,"reviewer",true,"已复核");
  verify(community).saveReviewed(eq(7L),eq("author"),argThat(f->f.body().equals(submitted.body())&&f.publish()));
  verify(db).update(startsWith("UPDATE article_reviews SET status=?"),eq("APPROVED"),eq("已复核"),eq(10L),eq(4L));
 }
 @Test void ordinaryMembersCannotReadOrDecideReviews(){role("MEMBER");assertThrows(ResponseStatusException.class,reviews::pending);assertThrows(ResponseStatusException.class,()->reviews.decide(4L,"author",true,""));}
}
