package cn.haiyan.platform.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArticleModerationThresholdTest {
 @Test void oneOrTwoHitsBelowFiftyPercentDoNotBlock(){
  assertTrue(ArticleModeration.check("博彩"+"景".repeat(5)).isEmpty());
  assertTrue(ArticleModeration.check("博彩、博彩"+"景".repeat(10)).isEmpty());
  assertTrue(ArticleModeration.check("广告代理，广告代理"+"景".repeat(20)).isEmpty());
  assertFalse(ArticleModeration.checkComment("博彩").isEmpty());
 }
 @Test void commonReligiousTermsAndDirectInsultsAreExcludedFromArticleButNotComments(){
  for(String word:new String[]{"基督教","操你妈","傻逼"}){
   assertTrue(ArticleModeration.check(word).isEmpty(),word);
   assertTrue(ArticleModeration.checkArticleMatch(word).isEmpty(),word);
  }
  assertFalse(ArticleModeration.checkComment("操你妈").isEmpty());
  assertFalse(ArticleModeration.checkComment("傻逼").isEmpty());
 }
 @Test void designatedSensitiveTermsRemainInArticleChecks(){
  assertFalse(ArticleModeration.checkArticleMatch("共产党").isEmpty());
  assertFalse(ArticleModeration.checkArticleMatch("博彩").isEmpty());
  assertFalse(ArticleModeration.checkArticleMatch("在教堂附近讨论共产党").isEmpty());
  assertFalse(ArticleModeration.checkArticleMatch("教堂附近出现博彩网站").isEmpty());
  assertFalse(ArticleModeration.check("共产党").isEmpty());
 }
 @Test void fiftyPercentThresholdIsInclusive(){
  assertFalse(ArticleModeration.check("共产党"+"景".repeat(3)).isEmpty());
  assertTrue(ArticleModeration.check("共产党"+"景".repeat(4)).isEmpty());
 }
 @Test void calibratedArticleVocabularyHonoursChosenExamples(){
  for(String word:new String[]{"接吻","拥抱","赤裸","自杀","枪支","毒品","革命","政府","民主","战争","招聘","代购","优惠","长期","抽取","骑士","黄色","泰安","胡锦","中出","人流","下身","潜规则","妹妹","姐姐","电影"}){
   assertTrue(ArticleModeration.checkArticleMatch(word).isEmpty(),word);
  }
  for(String word:new String[]{"共产党","博彩","兼职","https://example.test/path","example.com"})assertFalse(ArticleModeration.checkArticleMatch(word).isEmpty(),word);
  assertFalse(ArticleModeration.checkArticleMatch("色情").isEmpty());
 }
 @Test void articleNeedsThreeSeparateHitsAndTenPercentCoverage(){
  assertFalse(ArticleModeration.check("博彩、博彩、博彩").isEmpty());
  assertTrue(ArticleModeration.check("博彩、博彩、博彩"+"春风吹过校园".repeat(150)).isEmpty());
  assertFalse(ArticleModeration.check("博彩、博彩、博彩"+"景".repeat(52)).isEmpty());
  assertTrue(ArticleModeration.check("博彩、博彩、博彩"+"景".repeat(53)).isEmpty());
 }
}
