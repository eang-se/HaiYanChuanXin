package cn.haiyan.platform.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static cn.haiyan.platform.service.CommunityService.*;

@Service
public class ArticleReviewQuota {
    private static final int LIMIT=10;
    private final CommunityService community;
    public ArticleReviewQuota(CommunityService community){this.community=community;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void reserve(String username,Long workId,CommunityService.AiSnapshot snapshot,boolean manual){
        var member=community.member(username);
        if("PUBLIC".equals(member.get("accountType"))||ManagementAccess.has("ADMIN")||ManagementAccess.has("SUPER_ADMIN"))return;
        long id=((Number)member.get("id")).longValue();LocalDate day=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String hash=fingerprint(workId,snapshot);var db=community.store();
        db.update("INSERT IGNORE INTO article_review_daily_quota(member_id,review_day,attempts) VALUES(?,?,0)",id,day);
        var row=db.one("SELECT attempts FROM article_review_daily_quota WHERE member_id=? AND review_day=? FOR UPDATE",id,day);
        if(row==null)throw error(503,"审核次数暂不可用，请稍后重试");
        if(manual){
            var receipt=db.one("SELECT id FROM article_review_quota_receipts WHERE member_id=? AND review_day=? AND snapshot_hash=? AND manual_claimed=FALSE ORDER BY id DESC LIMIT 1 FOR UPDATE",id,day,hash);
            if(receipt!=null){db.update("UPDATE article_review_quota_receipts SET manual_claimed=TRUE WHERE id=?",receipt.get("id"));return;}
        }
        if(((Number)row.get("attempts")).intValue()>=LIMIT)throw error(429,"今日审核提交已达 10 次上限，请明天再试");
        db.update("UPDATE article_review_daily_quota SET attempts=attempts+1 WHERE member_id=? AND review_day=?",id,day);
        if(!manual)db.insert("INSERT INTO article_review_quota_receipts(member_id,review_day,snapshot_hash) VALUES(?,?,?)",id,day,hash);
    }
    static String fingerprint(Long workId,CommunityService.AiSnapshot snapshot){
        try{
            var digest=MessageDigest.getInstance("SHA-256");
            for(String value:List.of(workId==null?"new":workId.toString(),snapshot.body(),snapshot.cover(),
                    snapshot.fields().getOrDefault("title",""),snapshot.fields().getOrDefault("byline",""),snapshot.fields().getOrDefault("excerpt",""),snapshot.fields().getOrDefault("notes",""),snapshot.fields().getOrDefault("customGenre",""))){
                byte[] bytes=value.getBytes(StandardCharsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
