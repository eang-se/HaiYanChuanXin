package cn.haiyan.platform.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArticleModerationThresholdTest {
 @Test void oneOrTwoHitsBelowThirtyPercentDoNotBlock(){
  assertTrue(ArticleModeration.check("博彩"+"景".repeat(5)).isEmpty());
  assertTrue(ArticleModeration.check("博彩、博彩"+"景".repeat(10)).isEmpty());
  assertTrue(ArticleModeration.check("广告代理，广告代理"+"景".repeat(20)).isEmpty());
  assertFalse(ArticleModeration.checkComment("博彩").isEmpty());
 }
 @Test void aSingleDirectInsultAtHighDensityIsBlocked(){
  assertFalse(ArticleModeration.checkMatch("操你妈").isEmpty());
  assertFalse(ArticleModeration.check("标题 佚名 操你妈").isEmpty());
 }
 @Test void thirtyPercentThresholdIsInclusive(){
  assertFalse(ArticleModeration.check("操你妈"+"景".repeat(7)).isEmpty());
  assertTrue(ArticleModeration.check("操你妈"+"景".repeat(8)).isEmpty());
 }
 @Test void articleNeedsThreeSeparateHitsAndOnePercentCoverage(){
  assertFalse(ArticleModeration.check("博彩、博彩、博彩").isEmpty());
  assertTrue(ArticleModeration.check("博彩、博彩、博彩"+"春风吹过校园".repeat(150)).isEmpty());
  assertFalse(ArticleModeration.check("博彩、博彩、博彩"+"景".repeat(592)).isEmpty());
  assertTrue(ArticleModeration.check("博彩、博彩、博彩"+"景".repeat(593)).isEmpty());
 }
}
