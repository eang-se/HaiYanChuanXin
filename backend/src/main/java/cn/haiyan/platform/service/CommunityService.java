package cn.haiyan.platform.service;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CommunityService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final CommunityStore db;
    private final WorkCollections collections;private final AccountPermissions permissions;private final PenNames penNames;
    public CommunityService(CommunityStore db,WorkCollections collections,AccountPermissions permissions,PenNames penNames) { this.db=db;this.collections=collections;this.permissions=permissions;this.penNames=penNames; }
    public CommunityStore store() { return db; }
    public static ResponseStatusException error(int code,String message) { return new ResponseStatusException(HttpStatus.valueOf(code),message); }
    public Map<String,Object> member(String username) {
        var m=db.one("SELECT id,username,pen_name,bio,role,account_type,signature,qq_number,avatar_url,profile_completed,profile_prompt_seen,created_at FROM members WHERE username=? AND deleted_at IS NULL",username);
        if(m==null) throw error(401,"请先登录");m.put("avatarUrl",avatarUrl(m.get("id"),m.get("qqNumber"),m.get("accountType"),m.get("username"),m.get("avatarUrl")));m.put("permissions",permissions.effective(((Number)m.get("id")).longValue(),(String)m.get("role"))); return m;
    }
    public long memberId(String username) { return ((Number)member(username).get("id")).longValue(); }
    public static String avatarUrl(Object id,Object qq) { return qq instanceof String q && q.matches("[1-9][0-9]{4,11}") ? "/api/public/avatars/"+id : ""; }
    public static String avatarUrl(Object id,Object qq,Object type,Object username) { return avatarUrl(id,qq,type,username,null); }
    public static String avatarUrl(Object id,Object qq,Object type,Object username,Object custom) { if("PUBLIC".equals(type)){if(custom instanceof String url&&url.matches("/api/public/account-avatars/[0-9a-f-]{36}"))return url;return switch(String.valueOf(username)) {case "haiyan_official" -> "/brand/haiyan-seal.webp";case "haiyan_editor" -> "/brand/editorial-account.jpg";case "haiyan_tech" -> "/brand/tech-department.webp";case "haiyan_activity" -> "/brand/activity-account.webp";default -> "";};}return avatarUrl(id,qq); }
    public static final String AVATAR_SQL="CASE WHEN m.account_type='PUBLIC' THEN CASE WHEN m.avatar_url REGEXP '^/api/public/account-avatars/[0-9a-f-]{36}$' THEN m.avatar_url ELSE CASE m.username WHEN 'haiyan_official' THEN '/brand/haiyan-seal.webp' WHEN 'haiyan_editor' THEN '/brand/editorial-account.jpg' WHEN 'haiyan_tech' THEN '/brand/tech-department.webp' WHEN 'haiyan_activity' THEN '/brand/activity-account.webp' ELSE '' END END WHEN m.qq_number REGEXP '^[1-9][0-9]{4,11}$' THEN CONCAT('/api/public/avatars/',m.id) ELSE '' END";
    public static final String WORK_SELECT="SELECT w.*,COALESCE(pn.name,NULLIF(w.byline,''),m.pen_name) AS display_byline,m.pen_name AS author_name,m.account_type AS author_account_type,"+AVATAR_SQL+" AS author_avatar,(SELECT COUNT(*) FROM works pw WHERE pw.pen_name_id=w.pen_name_id AND pw.status='PUBLISHED') AS pen_name_work_count,(SELECT COUNT(*) FROM reactions r WHERE r.work_id=w.id AND r.kind='LIKE') AS likes,(SELECT COUNT(*) FROM comments c WHERE c.work_id=w.id AND c.visible=TRUE) AS comment_count FROM works w JOIN members m ON m.id=w.author_id LEFT JOIN pen_names pn ON pn.id=w.pen_name_id ";
    public List<Map<String,Object>> decorate(List<Map<String,Object>> rows,boolean detail) {
        return decorate(rows,detail,null);
    }
    public List<Map<String,Object>> decorate(List<Map<String,Object>> rows,boolean detail,Long viewer) {
        if(rows.isEmpty())return rows;
        // Fetch related data in batches rather than performing two queries per work.
        Map<Long,List<Map<String,Object>>> tagsByWork=new HashMap<>();
        Map<Long,Map<String,Object>> collectionsById=new HashMap<>();
        List<Long> workIds=rows.stream().map(w->((Number)w.get("id")).longValue()).distinct().toList();
        List<Long> collectionIds=rows.stream().map(w->w.get("collectionId")).filter(Number.class::isInstance).map(x->((Number)x).longValue()).distinct().toList();
        for(int start=0;start<workIds.size();start+=200){
            var ids=workIds.subList(start,Math.min(start+200,workIds.size()));
            String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
            for(var tag:db.list("SELECT wt.work_id AS decorated_work_id,t.* FROM tags t JOIN work_tags wt ON wt.tag_id=t.id WHERE wt.work_id IN ("+placeholders+") AND t.kind='TOPIC'",ids.toArray())){
                long workId=((Number)tag.remove("decoratedWorkId")).longValue();
                tagsByWork.computeIfAbsent(workId,k->new ArrayList<>()).add(tag);
            }
        }
        for(int start=0;start<collectionIds.size();start+=200){
            var ids=collectionIds.subList(start,Math.min(start+200,collectionIds.size()));
            String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
            for(var collection:db.list("SELECT id,title,kind,published FROM collections WHERE id IN ("+placeholders+")",ids.toArray()))collectionsById.put(((Number)collection.get("id")).longValue(),collection);
        }
        for(var w:rows) {
            w.remove("editRevision");
            w.put("layout",ArticleLayout.decode(w.remove("bodyLayout"),String.valueOf(w.get("genre"))));
            boolean own=viewer!=null&&((Number)w.get("authorId")).longValue()==viewer;
            w.put("tags",tagsByWork.getOrDefault(((Number)w.get("id")).longValue(),List.of()));
            Object collectionId=w.get("collectionId");
            var collection=collectionId instanceof Number n?collectionsById.get(n.longValue()):null;
            Object published=collection==null?null:collection.get("published");
            if(!own && !(Boolean.TRUE.equals(published)||(published instanceof Number n&&n.intValue()!=0)))collection=null;
            w.put("collection",collection);
            if(collection==null&&!own){w.put("collectionId",null);w.put("collectionPosition",null);}
            String plain=String.valueOf(w.get("plainText")).replaceAll("\\s","");
            w.put("wordCount",plain.codePointCount(0,plain.length()));
            w.remove("plainText"); if(!detail)w.remove("body");
        } return rows;
    }
    public Map<String,Object> work(long id, String username) {
        var rows=db.list(WORK_SELECT+"WHERE w.id=?",id);
        if(rows.isEmpty())throw error(404,"作品不存在");
        var w=rows.getFirst();
        Long viewer=username==null?null:memberId(username);
        boolean published="PUBLISHED".equals(w.get("status"));
        if(!published && (viewer==null || !Objects.equals(((Number)w.get("authorId")).longValue(),viewer)))throw error(404,"作品尚未公开");
        decorate(rows,true,viewer);
        w.put("liked",viewer!=null && db.count("SELECT COUNT(*) FROM reactions WHERE member_id=? AND work_id=? AND kind='LIKE'",viewer,id)>0);
        w.put("bookmarked",viewer!=null && db.count("SELECT COUNT(*) FROM reactions WHERE member_id=? AND work_id=? AND kind='BOOKMARK'",viewer,id)>0);
        return w;
    }
    public void publicWork(long id) { if(db.count("SELECT COUNT(*) FROM works WHERE id=? AND status='PUBLISHED'",id)==0)throw error(404,"作品尚未公开"); }
    public void owner(long id,long member) { if(db.count("SELECT COUNT(*) FROM works WHERE id=? AND author_id=?",id,member)==0)throw error(404,"找不到你的稿件"); }
    @Transactional public void withdraw(long id,String username) {
        long author=memberId(username);permissions.require(username,"WRITE");
        db.one("SELECT id FROM members WHERE id=? FOR UPDATE",author);
        if(db.one("SELECT id FROM works WHERE id=? AND author_id=? FOR UPDATE",id,author)==null)throw error(404,"找不到你的稿件");
        db.update("UPDATE works SET status='WITHDRAWN',edit_revision=edit_revision+1 WHERE id=?",id);
    }
    @Transactional
    public void deleteDraft(long id,String username) {
        long author=memberId(username);permissions.require(username,"WRITE");
        permissions.requireOwnDeletion(username);
        // Use the same owner lock as save, so autosave cannot race a deletion or publication.
        db.one("SELECT id FROM members WHERE id=? FOR UPDATE",author);
        var work=db.one("SELECT status FROM works WHERE id=? AND author_id=? FOR UPDATE",id,author);
        if(work==null)throw error(404,"找不到你的稿件");
        if(!Set.of("DRAFT","WITHDRAWN").contains(String.valueOf(work.get("status"))))throw error(409,"请先撤回已发布作品，再删除稿件");
        deleteWorkRelations(id);
        if(db.update("DELETE FROM works WHERE id=? AND author_id=? AND status IN ('DRAFT','WITHDRAWN')",id,author)!=1)throw error(409,"稿件状态已变化，请刷新后重试");
        // Uploaded files remain owned by the author and may be used by another work.
    }
    public record WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,List<String> collectionTags,boolean publish,String byline,String sourceNote,String notes,String customGenre,ArticleLayout layout,Long collectionId,Long penNameId,Map<String,Object> bodyJson,Long draftRevision,List<Long> activityIds,List<String> contentTags,String bylineMode) {
        public WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,List<String> collectionTags,boolean publish,String byline,String sourceNote,String notes,String customGenre,ArticleLayout layout,Long collectionId,Long penNameId,Map<String,Object> bodyJson,Long draftRevision,List<Long> activityIds,List<String> contentTags){this(title,genre,excerpt,body,cover,tagIds,collectionTags,publish,byline,sourceNote,notes,customGenre,layout,collectionId,penNameId,bodyJson,draftRevision,activityIds,contentTags,null);}
        public WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,List<String> collectionTags,boolean publish,String byline,String sourceNote,String notes,String customGenre,ArticleLayout layout,Long collectionId,Long penNameId,Map<String,Object> bodyJson,Long draftRevision){this(title,genre,excerpt,body,cover,tagIds,collectionTags,publish,byline,sourceNote,notes,customGenre,layout,collectionId,penNameId,bodyJson,draftRevision,null,null);}
        public WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,List<String> collectionTags,boolean publish,String byline,String sourceNote,String notes,String customGenre,ArticleLayout layout,Long collectionId,Long penNameId){this(title,genre,excerpt,body,cover,tagIds,collectionTags,publish,byline,sourceNote,notes,customGenre,layout,collectionId,penNameId,null,null);}
        public WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,List<String> collectionTags,boolean publish,String byline,String sourceNote,String notes,String customGenre,ArticleLayout layout){this(title,genre,excerpt,body,cover,tagIds,collectionTags,publish,byline,sourceNote,notes,customGenre,layout,null,null,null,null);}
        public WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,List<String> collectionTags,boolean publish,String byline,String sourceNote,String notes){this(title,genre,excerpt,body,cover,tagIds,collectionTags,publish,byline,sourceNote,notes,null,null);}
        public WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,List<String> collectionTags,boolean publish,String byline,String sourceNote){this(title,genre,excerpt,body,cover,tagIds,collectionTags,publish,byline,sourceNote,null);}
        public WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,List<String> collectionTags,boolean publish){this(title,genre,excerpt,body,cover,tagIds,collectionTags,publish,null,null);}
        public WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,boolean publish){this(title,genre,excerpt,body,cover,tagIds,null,publish);}
    }
    @Transactional
    public Map<String,Object> save(Long id,String username,WorkInput input) { return saveInternal(id,username,input,false); }
    Map<String,Object> saveReviewed(Long id,String username,WorkInput input) { return saveInternal(id,username,input,true); }
    private Map<String,Object> saveInternal(Long id,String username,WorkInput input,boolean reviewed) {
        long author=memberId(username);
        if(!reviewed) permissions.require(username,"WRITE");
        db.one("SELECT id FROM members WHERE id=? FOR UPDATE",author);
        if(id!=null)owner(id,author);
        String title=bounded(input.title(),100,"标题");
        var state=id==null?null:db.one("SELECT status,edit_revision FROM works WHERE id=? FOR UPDATE",id);
        var existing=id==null?null:db.one("SELECT byline,source_note,notes,genre,custom_genre,body_layout,pen_name_id,collection_id FROM works WHERE id=?",id);
        long revision=state!=null&&state.get("editRevision") instanceof Number n?n.longValue():0;
        if(input.draftRevision()!=null&&input.draftRevision()!=revision)throw error(409,"稿件已在其他页面更新，请重新载入后再保存");
        if(input.draftRevision()==null&&revision>0)throw error(409,"编辑页面版本过旧，请刷新页面后再保存");
        String byline=bounded(input.byline()==null&&existing!=null?(String)existing.get("byline"):input.byline(),120,"作者署名");
        String sourceNote=bounded(input.sourceNote()==null&&existing!=null?(String)existing.get("sourceNote"):input.sourceNote(),500,"来源说明");
        String notes=bounded(input.notes()==null&&existing!=null?(String)existing.get("notes"):input.notes(),4000,"附注");
        if(!sourceNote.isBlank()){
            if(!notes.contains(sourceNote))notes=bounded(notes.isBlank()?sourceNote:notes+"\n\n"+sourceNote,4000,"附注");
            sourceNote="";
        }
        String requestedMode=input.bylineMode();
        if(requestedMode==null&&id!=null){var attribution=db.one("SELECT byline_mode FROM works WHERE id=?",id);if(attribution!=null)requestedMode=(String)attribution.get("bylineMode");}
        var credit=WorkAttribution.resolve(penNames,author,requestedMode,byline,input.penNameId(),existing);
        String bylineMode=credit.mode();byline=credit.byline();Long penNameId=credit.penNameId();
        String genre=bounded(input.genre(),30,"体裁");
        if(genre.isBlank())genre="其他";
        String customGenre=bounded(input.customGenre()==null&&existing!=null?(String)existing.get("customGenre"):input.customGenre(),30,"自定义体裁");
        if(!Set.of("诗歌","散文","小说","随笔","公告","其他").contains(genre)){if(customGenre.isBlank())customGenre=genre;genre="其他";}
        if(!genre.equals("其他"))customGenre="";
        if("公告".equals(customGenre))throw error(400,"公告请使用专用文章类型");
        if("公告".equals(genre))ManagementAccess.requireAdmin();
        ArticleLayout layout=input.layout()!=null?input.layout().validated():existing!=null&&Objects.equals(existing.get("genre"),genre)?ArticleLayout.decode(existing.get("bodyLayout"),genre):ArticleLayout.defaults(genre);
        String excerpt=bounded(input.excerpt(),240,"摘要");
        String raw=Objects.requireNonNullElse(input.body(),"");
        if(raw.length()>200000)throw error(400,"单篇正文请控制在 20 万字符以内");
        var document=WorkHtml.clean(raw);
        String body=document.body().html();
        WorkDocument.validate(input.bodyJson(),body);
        Set<String> mediaIds=new HashSet<>();
        String cover=Objects.requireNonNullElse(input.cover(),"");
        List<String> sources=new ArrayList<>();document.select("img").forEach(img->sources.add(img.attr("src")));if(!cover.isBlank())sources.add(cover);
        for(String src:sources) {
            if(!src.matches(WorkHtml.MEDIA_PATH))throw error(400,"请使用平台上传的图片");
            String mediaId=src.substring(src.lastIndexOf('/')+1);
            if(db.count("SELECT COUNT(*) FROM media_files WHERE id=? AND owner_id=?",mediaId,author)==0)throw error(400,"图片不存在或不属于当前账号");
            mediaIds.add(mediaId);
        }
        if(!mediaIds.isEmpty()){
            String placeholders=String.join(",",Collections.nCopies(mediaIds.size(),"?"));
            long imageBytes=db.count("SELECT COALESCE(SUM(size_bytes),0) FROM media_files WHERE id IN ("+placeholders+")",mediaIds.toArray());
            if(imageBytes>5L*1024*1024)throw error(400,"一篇文章的图片总大小不能超过 5 MB（含封面），请删除或压缩部分图片");
        }
        String plain=document.text();
        if(input.publish() && (title.isBlank() || plain.isBlank()))throw error(400,"发布前请填写标题和正文");
        if(input.publish()) {
            String text=title+" "+byline+" "+excerpt+" "+plain+" "+notes+" "+customGenre;
            if(text.indexOf('\u0000')>=0)throw error(400,"文本含有无效字符");
            var findings=ArticleModeration.check(text);
            if(!reviewed&&!findings.isEmpty()){
                Map<String,String> fields=new LinkedHashMap<>();fields.put("title",title);fields.put("byline",byline);fields.put("excerpt",excerpt);fields.put("notes",notes);fields.put("customGenre",customGenre);
                throw new ArticleModerationWarning(ArticleModerationWarning.locate(document,fields),document);
            }
        }
        if(input.collectionTags()!=null&&!input.collectionTags().isEmpty())throw error(400,"合集已改为独立属性，请刷新页面后选择一部合集");
        List<Long> tags=new WorkTags(db).resolve(id,input.tagIds(),input.activityIds(),input.contentTags());
        long currentCollection=existing!=null&&existing.get("collectionId") instanceof Number n?n.longValue():0;
        if(!reviewed&&input.collectionId()!=null&&input.collectionId()!=currentCollection)permissions.require(username,"COLLECTIONS");
        if(excerpt.isBlank())excerpt=plain.substring(0,Math.min(plain.length(),160));
        if(id!=null)db.update("UPDATE article_reviews SET status='SUPERSEDED',decided_at=CURRENT_TIMESTAMP WHERE work_id=? AND status='PENDING'",id);
        // Published rows and their media relations are the public snapshot. Draft edits must not mutate them.
        Long savedCollection=input.collectionId()!=null?input.collectionId():(currentCollection==0?null:currentCollection);
        if(id!=null&&!input.publish()&&state!=null&&"PUBLISHED".equals(state.get("status"))) {
            db.update("UPDATE works SET edit_revision=edit_revision+1,updated_at=updated_at WHERE id=?",id);
            saveDraft(id,revision,draftPayload(title,genre,excerpt,body,cover,tags,byline,sourceNote,notes,customGenre,layout,savedCollection,penNameId,input.bodyJson(),bylineMode),mediaIds);
            return Map.of("id",id,"status","PUBLISHED","draftRevision",revision+1);
        }
        String status=input.publish()?"PUBLISHED":"DRAFT";
        if(id==null)id=db.insert("INSERT INTO works(author_id,title,genre,excerpt,body,plain_text,cover,status,published_at,body_layout,custom_genre,pen_name_id,byline,source_note,notes,edit_revision) VALUES(?,?,?,?,?,?,?,?,IF(?='PUBLISHED',CURRENT_TIMESTAMP,NULL),?,?,?,?,?,?,1)",author,title,genre,excerpt,body,plain,cover,status,status,layout.encode(),customGenre,penNameId,byline,sourceNote,notes);
        else db.update("UPDATE works SET title=?,genre=?,excerpt=?,body=?,plain_text=?,cover=?,status=?,published_at=IF(?='PUBLISHED',COALESCE(published_at,CURRENT_TIMESTAMP),published_at),body_layout=?,custom_genre=?,pen_name_id=?,byline=?,source_note=?,notes=?,edit_revision=edit_revision+1 WHERE id=?",title,genre,excerpt,body,plain,cover,status,status,layout.encode(),customGenre,penNameId,byline,sourceNote,notes,id);
        db.update("UPDATE works SET byline_mode=?,updated_at=updated_at WHERE id=?",bylineMode,id);
        db.update("DELETE wt FROM work_tags wt JOIN tags t ON t.id=wt.tag_id WHERE wt.work_id=? AND t.name<>'海燕精选'",id);
        for(long tag:tags)db.update("INSERT INTO work_tags(work_id,tag_id) VALUES(?,?)",id,tag);
        collections.assign(id,author,input.collectionId());
        db.update("DELETE FROM work_media WHERE work_id=?",id);
        for(String mid:mediaIds)db.update("INSERT INTO work_media(work_id,media_id) VALUES(?,?)",id,mid);
        saveDraft(id,revision,draftPayload(title,genre,excerpt,body,cover,tags,byline,sourceNote,notes,customGenre,layout,savedCollection,penNameId,input.bodyJson(),bylineMode),mediaIds);
        if(input.publish()) {
            long versionId=db.insert("INSERT INTO work_versions(work_id,title,body,body_json) VALUES(?,?,?,?)",id,title,body,input.bodyJson()==null?null:JSON.writeValueAsString(input.bodyJson()));
            for(String mid:mediaIds)db.update("INSERT INTO work_version_media(version_id,media_id) VALUES(?,?)",versionId,mid);
        }
        return Map.of("id",id,"status",status,"draftRevision",revision+1);
    }
    private Map<String,Object> draftPayload(String title,String genre,String excerpt,String body,String cover,List<Long> tags,String byline,String sourceNote,String notes,String customGenre,ArticleLayout layout,Long collectionId,Long penNameId,Map<String,Object> bodyJson,String bylineMode) {
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("title",title);payload.put("genre",genre);payload.put("excerpt",excerpt);payload.put("body",body);payload.put("cover",cover);payload.put("tagIds",tags);
        payload.put("byline",byline);payload.put("sourceNote",sourceNote);payload.put("notes",notes);payload.put("customGenre",customGenre);payload.put("layout",layout);
        payload.put("collectionId",collectionId);payload.put("penNameId",penNameId);payload.put("bodyJson",bodyJson);payload.put("bylineMode",bylineMode);return payload;
    }
    private void saveDraft(long id,long revision,Map<String,Object> payload,Set<String> mediaIds) {
        String data=JSON.writeValueAsString(payload);if(data.length()>1000000)throw error(400,"稿件数据过大");
        db.update("INSERT INTO work_drafts(work_id,payload,revision) VALUES(?,?,?) ON DUPLICATE KEY UPDATE payload=VALUES(payload),revision=VALUES(revision)",id,data,revision+1);
        db.update("DELETE FROM work_draft_media WHERE work_id=?",id);
        for(String mid:mediaIds)db.update("INSERT INTO work_draft_media(work_id,media_id) VALUES(?,?)",id,mid);
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> draft(long id,String username) {
        long member=memberId(username);owner(id,member);var work=work(id,username);
        var row=db.one("SELECT d.payload,w.edit_revision AS revision FROM works w LEFT JOIN work_drafts d ON d.work_id=w.id WHERE w.id=?",id);
        if(row==null||row.get("payload")==null){work.put("bodyJson",null);work.put("draftRevision",row==null?0:row.get("revision"));return work;}
        Map<String,Object> data=JSON.readValue((String)row.get("payload"),Map.class);
        Object tagIds=data.remove("tagIds");work.putAll(data);work.put("draftRevision",row.get("revision"));
        if(tagIds instanceof List<?> ids) {
            List<Map<String,Object>> tags=new ArrayList<>();
            for(Object tag:ids){var found=db.one("SELECT * FROM tags WHERE id=?",tag);if(found!=null)tags.add(found);}work.put("tags",tags);
        }
        return work;
    }
    public List<Map<String,Object>> versions(long id,String username) {owner(id,memberId(username));return db.list("SELECT id,title,created_at FROM work_versions WHERE work_id=? ORDER BY id DESC LIMIT 100",id);}
    private void deleteWorkRelations(long id){
        db.update("DELETE vm FROM work_version_media vm JOIN work_versions v ON v.id=vm.version_id WHERE v.work_id=?",id);
        for(String table:List.of("work_tags","work_media","collection_works","reactions","comments"))db.update("DELETE FROM "+table+" WHERE work_id=?",id);
    }
    private Map<String,Object> lockManagedWork(long id){
        var owner=db.one("SELECT author_id FROM works WHERE id=?",id);
        if(owner==null)throw error(404,"文章不存在");
        db.one("SELECT id FROM members WHERE id=? FOR UPDATE",owner.get("authorId"));
        var work=db.one("SELECT * FROM works WHERE id=? FOR UPDATE",id);
        if(work==null||"DRAFT".equals(work.get("status")))throw error(404,"文章不存在或尚未发布");
        return work;
    }
    @Transactional public void deleteArticle(long id){
        ManagementAccess.requireAdmin();lockManagedWork(id);deleteWorkRelations(id);
        if(db.update("DELETE FROM works WHERE id=? AND status<>'DRAFT'",id)!=1)throw error(409,"文章状态已变化，请刷新后重试");
    }
    @Transactional public void reviewArticle(long id,boolean approve){
        if(!ManagementAccess.canReview())throw error(403,"需要文章审核权限");
        var work=lockManagedWork(id);String target=approve?"PUBLISHED":"WITHDRAWN";
        if(target.equals(work.get("status")))return;
        if(approve){
            var body=WorkHtml.clean(String.valueOf(work.get("body")));String title=String.valueOf(work.get("title"));
            if(title.isBlank()||body.text().isBlank())throw error(400,"标题和正文不能为空");
            // Review restores the last submitted public snapshot, never its private draft.
            db.update("UPDATE works SET status='PUBLISHED',published_at=COALESCE(published_at,CURRENT_TIMESTAMP),edit_revision=edit_revision+1 WHERE id=?",id);
        }else db.update("UPDATE works SET status='WITHDRAWN',edit_revision=edit_revision+1 WHERE id=?",id);
    }
    @SuppressWarnings("unchecked") public Map<String,Object> version(long id,long versionId,String username) {
        owner(id,memberId(username));var row=db.one("SELECT id,title,body,body_json,created_at FROM work_versions WHERE id=? AND work_id=?",versionId,id);
        if(row==null)throw error(404,"版本不存在");Object json=row.get("bodyJson");if(json instanceof String value)row.put("bodyJson",JSON.readValue(value,Map.class));return row;
    }
    public static String bounded(String value,int max,String label) {
        String s=Objects.requireNonNullElse(value,"").trim(); if(s.length()>max)throw error(400,label+"过长");return s;
    }
    @Transactional public void deleteActivity(long id){
        if(db.one("SELECT id FROM tags WHERE id=? AND kind='TOPIC' AND is_activity=TRUE FOR UPDATE",id)==null)throw error(404,"活动不存在");
        db.update("DELETE FROM work_tags WHERE tag_id=?",id);
        db.update("DELETE FROM tags WHERE id=? AND kind='TOPIC' AND is_activity=TRUE",id);
    }
    public static void checkText(String value) {
        if(value.matches("(?s).*([\\p{L}\\p{N}])\\1{40,}.*"))throw error(400,"检测到大量重复字符，请整理正文后再发布");
        if(value.indexOf('\u0000')>=0)throw error(400,"文本含有无效字符");
    }
}
