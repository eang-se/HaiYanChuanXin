package cn.haiyan.platform.service;
/** QQ membership and optional roster name provider. */
public interface QqEligibility { boolean accepts(String qq); default String displayName(String qq){return null;} }
