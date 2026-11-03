package cn.haiyan.platform.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArticleModerationThresholdTest {
 @Test void oneOrTwoHitsDoNotBlockEvenWhenTheyOccupyMostOfText(){
  assertTrue(ArticleModeration.check("博彩").isEmpty());
  assertTrue(ArticleModeration.check("博彩、博彩").isEmpty());
  assertTrue(ArticleModeration.check("广告代理，广告代理").isEmpty());
  assertFalse(ArticleModeration.checkComment("博彩").isEmpty());
 }
 @Test void articleNeedsThreeSeparateHitsAndOnePercentCoverage(){
  assertFalse(ArticleModeration.check("博彩、博彩、博彩").isEmpty());
  assertTrue(ArticleModeration.check("博彩、博彩、博彩"+"春风吹过校园".repeat(150)).isEmpty());
  assertFalse(ArticleModeration.check("博彩、博彩、博彩"+"景".repeat(592)).isEmpty());
  assertTrue(ArticleModeration.check("博彩、博彩、博彩"+"景".repeat(593)).isEmpty());
 }
}
