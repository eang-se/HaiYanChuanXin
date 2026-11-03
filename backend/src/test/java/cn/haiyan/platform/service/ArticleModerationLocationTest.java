package cn.haiyan.platform.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArticleModerationLocationTest {
 @Test void identifiesWholeBlocksAndFieldsWithoutReturningText(){
  var doc=WorkHtml.clean("<p>普通一段</p><p></p><blockquote><p>这是博彩的报道</p><p>另一段</p></blockquote><p>继续阅读</p>");
  var locations=ArticleModerationWarning.locate(doc,Map.of("title","博彩标题","excerpt","普通摘要"));
  assertTrue(locations.contains(new ArticleModerationWarning.Location("title",null,null)));
  assertTrue(locations.contains(new ArticleModerationWarning.Location("body",3,"blockquote")));
  assertEquals(2,locations.size());
  assertFalse(locations.toString().contains("博彩"));
 }
 @Test void listAndImageCaptionsAreLocatedAsEntireBlocks(){
  var doc=WorkHtml.clean("<ul><li><p>博彩内容</p></li></ul><figure><img src='/api/media/12345678-1234-1234-1234-123456789abc'><figcaption>博彩说明</figcaption></figure>");
  var locations=ArticleModerationWarning.locate(doc,Map.of());
  assertEquals(List.of(new ArticleModerationWarning.Location("body",1,"ul"),new ArticleModerationWarning.Location("body",2,"figure")),locations);
 }
}
