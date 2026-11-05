package cn.haiyan.platform.service;

import java.util.*;
import java.net.http.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class AiArticleModerationTest {
 private static final ObjectMapper JSON=new ObjectMapper();
 private String reply(Object verdict,Object locations,String finish){
  var content=JSON.writeValueAsString(Map.of("verdict",verdict,"locations",locations));
  return JSON.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason",finish,"message",Map.of("content",content)))));
 }
 @Test void acceptsValidPassAndMapsOnlyExistingBlockLocations(){
  var doc=WorkHtml.clean("<p>第一段</p><blockquote>第二段</blockquote>");var fields=Map.of("title","标题");
  assertFalse(AiArticleModeration.parse(reply("PASS",List.of(),"stop"),doc,fields).review());
  var reviewed=AiArticleModeration.parse(reply("REVIEW",List.of("field:title","body:2"),"stop"),doc,fields);
  assertTrue(reviewed.review());assertEquals(List.of(new ArticleModerationWarning.Location("title",null,null),new ArticleModerationWarning.Location("body",2,"blockquote")),reviewed.locations());
 }
 private static String requestBody(HttpRequest request) throws Exception {
  var bytes=new ByteArrayOutputStream();var done=new CountDownLatch(1);
  request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){
   public void onSubscribe(Flow.Subscription subscription){subscription.request(Long.MAX_VALUE);}
   public void onNext(ByteBuffer value){byte[] data=new byte[value.remaining()];value.get(data);bytes.writeBytes(data);}
   public void onError(Throwable error){done.countDown();}
   public void onComplete(){done.countDown();}
  });
  assertTrue(done.await(5,TimeUnit.SECONDS));return bytes.toString(StandardCharsets.UTF_8);
 }
 @Test void sendsTheWholeArticleAndHandlesServiceFailures() throws Exception {
  var http=mock(HttpClient.class);@SuppressWarnings("unchecked") var response=(HttpResponse<String>)mock(HttpResponse.class);
  when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn(reply("PASS",List.of(),"stop"));
  when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
  var ai=new AiArticleModeration("https://api.deepseek.com/chat/completions","test-key","deepseek-flash",http);
  var doc=WorkHtml.clean("<p>一段正文</p><p>第二段正文</p>");
  assertFalse(ai.assess(doc,Map.of("title","标题")).review());
  var captured=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);verify(http).send(captured.capture(),any(HttpResponse.BodyHandler.class));
  assertEquals("https://api.deepseek.com/chat/completions",captured.getValue().uri().toString());
  String body=requestBody(captured.getValue());
  for(String policy:List.of("垃圾和灌水", "中国政治敏感人物或事件", "危害国家安全", "煽动恐怖活动", "宣扬邪教", "营销推广", "辱骂或诽谤", "淫秽色情场景的直接描写"))assertTrue(body.contains(policy),policy);
  assertTrue(body.contains("只有当辱骂或攻击是文章的主要内容时"));
  assertTrue(body.contains("人物对白"));
  assertTrue(body.contains("疑似捏造事实损害真实人物名誉"));
  assertTrue(body.contains("一段正文"));assertTrue(body.contains("第二段正文"));
  assertEquals("Bearer test-key",captured.getValue().headers().firstValue("Authorization").orElseThrow());
  when(response.statusCode()).thenReturn(429);
  assertEquals(503,assertThrows(ResponseStatusException.class,()->ai.assess(doc,Map.of("title","标题"))).getStatusCode().value());
 }
 @Test void sendsImagesWithTheirLocationsAndMapsCover() throws Exception {
  var http=mock(HttpClient.class);@SuppressWarnings("unchecked") var response=(HttpResponse<String>)mock(HttpResponse.class);
  when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn(reply("REVIEW",List.of("field:cover"),"stop"));
  when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
  var ai=new AiArticleModeration("https://api.deepseek.com/chat/completions","test-key","deepseek-flash",http);
  var doc=WorkHtml.clean("<p>正文</p>");
  var result=ai.assess(doc,Map.of("title","标题"),List.of(new AiArticleModeration.Image(List.of("field:cover"),new byte[]{1,2,3})));
  assertTrue(result.review());assertEquals(List.of(new ArticleModerationWarning.Location("cover",null,null)),result.locations());
  var captured=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);verify(http).send(captured.capture(),any(HttpResponse.BodyHandler.class));
  String body=requestBody(captured.getValue());assertTrue(body.contains("field:cover"));assertTrue(body.contains("data:image/jpeg;base64,AQID"));
  assertThrows(ResponseStatusException.class,()->AiArticleModeration.parse(reply("REVIEW",List.of("field:cover"),"stop"),doc,Map.of("title","标题")));
 }
 @Test void truncatedMalformedAndUnknownLocationsNeverPass(){
  var doc=WorkHtml.clean("<p>正文</p>");var fields=Map.of("title","标题");
  for(String response:List.of(reply("PASS",List.of(),"length"),reply("PASS",List.of("body:1"),"stop"),reply("REVIEW",List.of("body:2"),"stop"),reply("REVIEW",List.of("field:secret"),"stop"),"{}")){
   assertEquals(503,assertThrows(ResponseStatusException.class,()->AiArticleModeration.parse(response,doc,fields)).getStatusCode().value());
  }
 }
}
