package cn.haiyan.platform.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticlePublicationServiceTest {
 private final CommunityService community=mock(CommunityService.class);
 private final AiArticleModeration ai=mock(AiArticleModeration.class);
 private final ArticleReviewQuota quota=mock(ArticleReviewQuota.class);
 private final ArticlePublicationService service=new ArticlePublicationService(community,ai,quota);
 private final CommunityService.WorkInput input=new CommunityService.WorkInput("标题","散文","","<p>正文</p>","",List.of(),true);
 private final CommunityService.AiSnapshot snapshot=new CommunityService.AiSnapshot("<p>正文</p>",Map.of("title","标题"));
 @Test void approvedSnapshotIsTheOnlyVersionPassedForPublication(){
  when(community.prepareForAi(5L,"author",input)).thenReturn(snapshot);when(ai.assess(any(),eq(snapshot.fields()),anyList())).thenReturn(new AiArticleModeration.Result(false,List.of()));
  when(community.saveWithAi(5L,"author",input,snapshot)).thenReturn(Map.of("id",5));
  assertEquals(5,service.save(5L,"author",input).get("id"));verify(community).saveWithAi(5L,"author",input,snapshot);
 }
 @Test void uncertainAndUnavailableReviewNeverPublish(){
  when(community.prepareForAi(5L,"author",input)).thenReturn(snapshot);
  when(ai.assess(any(),any(),anyList())).thenReturn(new AiArticleModeration.Result(true,List.of(new ArticleModerationWarning.Location("body",1,"p")))).thenThrow(CommunityService.error(503,"不可用"));
  assertEquals(1,assertThrows(ArticleModerationWarning.class,()->service.save(5L,"author",input)).locations().size());
  assertTrue(assertThrows(ArticleModerationWarning.class,()->service.save(5L,"author",input)).getReason().contains("暂不可用"));
  verify(community,never()).saveWithAi(any(),anyString(),any(),any());
 }
}
