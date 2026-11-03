package cn.haiyan.platform.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static cn.haiyan.platform.service.CommunityService.*;

@Service
public class ArticleReviewService {
    private final CommunityService community;
    private static final ObjectMapper JSON=new ObjectMapper();
    public ArticleReviewService(CommunityService community){this.community=community;}
    private WorkInput copy(WorkInput f,boolean publish,Long revision){
        return new WorkInput(f.title(),f.genre(),f.excerpt(),f.body(),f.cover(),f.tagIds(),f.collectionTags(),publish,f.byline(),f.sourceNote(),f.notes(),f.customGenre(),f.layout(),f.collectionId(),f.penNameId(),f.bodyJson(),revision,f.activityIds(),f.contentTags(),f.bylineMode());
    }
    @Transactional public Map<String,Object> submit(Long id,String username,WorkInput input){
        if(!input.publish())throw error(400,"请提交需要发布的稿件");
        String plain=WorkHtml.clean(Objects.requireNonNullElse(input.body(),"")).text();
        if(Objects.requireNonNullElse(input.title(),"").isBlank()||plain.isBlank())throw error(400,"发布前请填写标题和正文");
        String text=String.join(" ",Objects.requireNonNullElse(input.title(),""),Objects.requireNonNullElse(input.byline(),""),Objects.requireNonNullElse(input.excerpt(),""),plain,Objects.requireNonNullElse(input.notes(),""),Objects.requireNonNullElse(input.sourceNote(),""),Objects.requireNonNullElse(input.customGenre(),""));
        if(text.indexOf('\u0000')>=0)throw error(400,"文本含有无效字符");
        var findings=ArticleModeration.check(text);
        if(findings.isEmpty())throw error(400,"此版本未触发内容检查，请直接发布");
        var saved=community.save(id,username,copy(input,false,input.draftRevision()));
        long work=((Number)saved.get("id")).longValue(),revision=((Number)saved.get("draftRevision")).longValue();
        community.store().insert("INSERT INTO article_reviews(work_id,draft_revision,payload,findings) VALUES(?,?,?,?)",work,revision,JSON.writeValueAsString(copy(input,true,revision)),String.join("；",findings));
        return Map.of("id",work,"draftRevision",revision,"status","PENDING");
    }
    public List<Map<String,Object>> pending(){
        requireReviewer();
        return community.store().list("SELECT r.id,r.work_id,r.findings,r.created_at,JSON_UNQUOTE(JSON_EXTRACT(r.payload,'$.title')) AS title,m.pen_name AS author_name FROM article_reviews r JOIN works w ON w.id=r.work_id JOIN members m ON m.id=w.author_id WHERE r.status='PENDING' ORDER BY r.id DESC");
    }
    public Map<String,Object> detail(long id){
        requireReviewer();
        var row=community.store().one("SELECT r.id,r.work_id,r.payload,r.findings,r.created_at,r.status,m.pen_name AS author_name FROM article_reviews r JOIN works w ON w.id=r.work_id JOIN members m ON m.id=w.author_id WHERE r.id=? AND r.status='PENDING'",id);
        if(row==null)throw error(404,"复核申请不存在或已处理");
        row.put("submission",JSON.readValue((String)row.remove("payload"),WorkInput.class));return row;
    }
    public Map<String,Object> latest(long workId,String username){
        community.owner(workId,community.memberId(username));
        var row=community.store().one("SELECT status,findings,decision_note,created_at,decided_at FROM article_reviews WHERE work_id=? ORDER BY id DESC LIMIT 1",workId);
        return row==null?Map.of():row;
    }
    @Transactional public void decide(long id,String reviewer,boolean approve,String rawNote){
        requireReviewer();
        // Lock the work first, matching the save path; an optimistic revision check prevents stale publication.
        var lookup=community.store().one("SELECT work_id FROM article_reviews WHERE id=?",id);
        if(lookup==null)throw error(404,"复核申请不存在");
        long workId=((Number)lookup.get("workId")).longValue();
        var owner=community.store().one("SELECT w.author_id,m.username FROM works w JOIN members m ON m.id=w.author_id WHERE w.id=?",workId);
        if(owner==null)throw error(404,"文章不存在");
        community.store().one("SELECT id FROM members WHERE id=? FOR UPDATE",owner.get("authorId"));
        var work=community.store().one("SELECT edit_revision FROM works WHERE id=? FOR UPDATE",workId);
        var row=community.store().one("SELECT * FROM article_reviews WHERE id=? FOR UPDATE",id);
        if(row==null||!"PENDING".equals(row.get("status")))throw error(409,"申请已处理或稿件已修改，请刷新");
        if(work==null||((Number)work.get("editRevision")).longValue()!=((Number)row.get("draftRevision")).longValue())throw error(409,"稿件已修改，请作者重新提交复核");
        String note=bounded(rawNote,500,"复核意见");
        if(!approve&&note.isBlank())throw error(400,"请填写退回原因");
        long reviewerId=community.memberId(reviewer);
        if(approve){var input=JSON.readValue((String)row.get("payload"),WorkInput.class);community.saveReviewed(workId,(String)owner.get("username"),input);}
        community.store().update("UPDATE article_reviews SET status=?,decision_note=?,reviewer_id=?,decided_at=CURRENT_TIMESTAMP WHERE id=?",approve?"APPROVED":"REJECTED",note,reviewerId,id);
    }
    private static void requireReviewer(){if(!ManagementAccess.canReview())throw error(403,"需要文章审核权限");}
}
