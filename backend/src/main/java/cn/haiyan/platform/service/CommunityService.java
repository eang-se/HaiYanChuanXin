package cn.haiyan.platform.service;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommunityService {
    private final CommunityStore db;
    private final WorkCollections collections;private final AccountPermissions permissions;private final PenNames penNames;
    public CommunityService(CommunityStore db,WorkCollections collections,AccountPermissions permissions,PenNames penNames) { this.db=db;this.collections=collections;this.permissions=permissions;this.penNames=penNames; }
    public CommunityStore store() { return db; }
    public static ResponseStatusException error(int code,String message) { return new ResponseStatusException(HttpStatus.valueOf(code),message); }
    public Map<String,Object> member(String username) {
        var m=db.one("SELECT id,username,pen_name,bio,role,account_type,signature,qq_number,profile_completed,profile_prompt_seen,created_at FROM members WHERE username=?",username);
        if(m==null) throw error(401,"请先登录");m.put("avatarUrl",avatarUrl(m.get("id"),m.get("qqNumber"),m.get("accountType"),m.get("username")));m.put("permissions",permissions.effective(((Number)m.get("id")).longValue(),(String)m.get("role"))); return m;
    }
    public long memberId(String username) { return ((Number)member(username).get("id")).longValue(); }
    public static String avatarUrl(Object id,Object qq) { return qq instanceof String q && q.matches("[1-9][0-9]{4,11}") ? "/api/public/avatars/"+id : ""; }
    public static String avatarUrl(Object id,Object qq,Object type,Object username) { if("PUBLIC".equals(type))return switch(String.valueOf(username)) {case "haiyan_official" -> "/brand/haiyan-seal.webp";case "haiyan_editor" -> "/brand/haiyan-bird.svg";case "haiyan_tech" -> "/brand/tech-department.webp";case "haiyan_activity" -> "/brand/activity-account.webp";default -> "";};return avatarUrl(id,qq); }
    public static final String AVATAR_SQL="CASE WHEN m.account_type='PUBLIC' THEN CASE m.username WHEN 'haiyan_official' THEN '/brand/haiyan-seal.webp' WHEN 'haiyan_editor' THEN '/brand/haiyan-bird.svg' WHEN 'haiyan_tech' THEN '/brand/tech-department.webp' WHEN 'haiyan_activity' THEN '/brand/activity-account.webp' ELSE '' END WHEN m.qq_number REGEXP '^[1-9][0-9]{4,11}$' THEN CONCAT('/api/public/avatars/',m.id) ELSE '' END";
    public static final String WORK_SELECT="SELECT w.*,COALESCE(pn.name,NULLIF(w.byline,''),m.pen_name) AS display_byline,m.pen_name AS author_name,m.account_type AS author_account_type,"+AVATAR_SQL+" AS author_avatar,(SELECT COUNT(*) FROM works pw WHERE pw.pen_name_id=w.pen_name_id AND pw.status='PUBLISHED') AS pen_name_work_count,(SELECT COUNT(*) FROM reactions r WHERE r.work_id=w.id AND r.kind='LIKE') AS likes,(SELECT COUNT(*) FROM comments c WHERE c.work_id=w.id AND c.visible=TRUE) AS comment_count FROM works w JOIN members m ON m.id=w.author_id LEFT JOIN pen_names pn ON pn.id=w.pen_name_id ";
    public List<Map<String,Object>> decorate(List<Map<String,Object>> rows,boolean detail) {
        return decorate(rows,detail,null);
    }
    public List<Map<String,Object>> decorate(List<Map<String,Object>> rows,boolean detail,Long viewer) {
        for(var w:rows) {
            w.put("layout",ArticleLayout.decode(w.remove("bodyLayout"),String.valueOf(w.get("genre"))));
            boolean own=viewer!=null&&((Number)w.get("authorId")).longValue()==viewer;
            w.put("tags",db.list("SELECT t.* FROM tags t JOIN work_tags wt ON wt.tag_id=t.id WHERE wt.work_id=? AND t.kind='TOPIC'",w.get("id")));
            var collection=db.one("SELECT id,title,kind,published FROM collections WHERE id=?"+(own?"":" AND published=TRUE"),w.get("collectionId"));
            w.put("collection",collection);
            if(collection==null&&!own){w.put("collectionId",null);w.put("collectionPosition",null);}
            w.put("wordCount",String.valueOf(w.get("plainText")).replaceAll("\\s","").codePointCount(0,String.valueOf(w.get("plainText")).replaceAll("\\s","").length()));
            w.remove("plainText"); if(!detail)w.remove("body");
        } return rows;
    }
    public Map<String,Object> work(long id, String username) {
        var rows=db.list(WORK_SELECT+"WHERE w.id=?",id);
        if(rows.isEmpty())throw error(404,"作品不存在");
        var w=rows.getFirst();
        boolean published="PUBLISHED".equals(w.get("status"));
        if(!published && (username==null || !Objects.equals(w.get("authorId"),member(username).get("id"))))throw error(404,"作品尚未公开");
        decorate(rows,true,username==null?null:memberId(username));
        w.put("liked",username!=null && db.count("SELECT COUNT(*) FROM reactions WHERE member_id=? AND work_id=? AND kind='LIKE'",memberId(username),id)>0);
        w.put("bookmarked",username!=null && db.count("SELECT COUNT(*) FROM reactions WHERE member_id=? AND work_id=? AND kind='BOOKMARK'",memberId(username),id)>0);
        return w;
    }
    public void publicWork(long id) { if(db.count("SELECT COUNT(*) FROM works WHERE id=? AND status='PUBLISHED'",id)==0)throw error(404,"作品尚未公开"); }
    public void owner(long id,long member) { if(db.count("SELECT COUNT(*) FROM works WHERE id=? AND author_id=?",id,member)==0)throw error(404,"找不到你的稿件"); }
    @Transactional
    public void deleteDraft(long id,String username) {
        long author=memberId(username);permissions.require(username,"WRITE");
        // Use the same owner lock as save, so autosave cannot race a deletion or publication.
        db.one("SELECT id FROM members WHERE id=? FOR UPDATE",author);
        var work=db.one("SELECT status FROM works WHERE id=? AND author_id=? FOR UPDATE",id,author);
        if(work==null)throw error(404,"找不到你的稿件");
        if(!Set.of("DRAFT","WITHDRAWN").contains(String.valueOf(work.get("status"))))throw error(409,"请先撤回已发布作品，再删除稿件");
        for(String table:List.of("work_tags","work_media","collection_works","reactions","comments"))db.update("DELETE FROM "+table+" WHERE work_id=?",id);
        if(db.update("DELETE FROM works WHERE id=? AND author_id=? AND status IN ('DRAFT','WITHDRAWN')",id,author)!=1)throw error(409,"稿件状态已变化，请刷新后重试");
        // Uploaded files remain owned by the author and may be used by another work.
    }
    public record WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,List<String> collectionTags,boolean publish,String byline,String sourceNote,String notes,String customGenre,ArticleLayout layout,Long collectionId,Long penNameId) {
        public WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,List<String> collectionTags,boolean publish,String byline,String sourceNote,String notes,String customGenre,ArticleLayout layout){this(title,genre,excerpt,body,cover,tagIds,collectionTags,publish,byline,sourceNote,notes,customGenre,layout,null,null);}
        public WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,List<String> collectionTags,boolean publish,String byline,String sourceNote,String notes){this(title,genre,excerpt,body,cover,tagIds,collectionTags,publish,byline,sourceNote,notes,null,null);}
        public WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,List<String> collectionTags,boolean publish,String byline,String sourceNote){this(title,genre,excerpt,body,cover,tagIds,collectionTags,publish,byline,sourceNote,null);}
        public WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,List<String> collectionTags,boolean publish){this(title,genre,excerpt,body,cover,tagIds,collectionTags,publish,null,null);}
        public WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,boolean publish){this(title,genre,excerpt,body,cover,tagIds,null,publish);}
    }
    @Transactional
    public Map<String,Object> save(Long id,String username,WorkInput input) {
        long author=memberId(username);
        permissions.require(username,"WRITE");
        db.one("SELECT id FROM members WHERE id=? FOR UPDATE",author);
        if(id!=null)owner(id,author);
        String title=bounded(input.title(),100,"标题");
        var existing=id==null?null:db.one("SELECT byline,source_note,notes,genre,custom_genre,body_layout,pen_name_id,collection_id FROM works WHERE id=?",id);
        String byline=bounded(input.byline()==null&&existing!=null?(String)existing.get("byline"):input.byline(),120,"作者署名");
        String sourceNote=bounded(input.sourceNote()==null&&existing!=null?(String)existing.get("sourceNote"):input.sourceNote(),500,"来源说明");
        String notes=bounded(input.notes()==null&&existing!=null?(String)existing.get("notes"):input.notes(),4000,"附注");
        if(!sourceNote.isBlank()){
            if(!notes.contains(sourceNote))notes=bounded(notes.isBlank()?sourceNote:notes+"\n\n"+sourceNote,4000,"附注");
            sourceNote="";
        }
        var pen=penNames.resolve(author,input.penNameId(),existing);
        Long penNameId=pen==null?null:((Number)pen.get("id")).longValue();
        if(pen!=null)byline=(String)pen.get("name");
        String genre=bounded(input.genre(),30,"体裁");
        if(genre.isBlank())genre="其他";
        String customGenre=bounded(input.customGenre()==null&&existing!=null?(String)existing.get("customGenre"):input.customGenre(),30,"自定义体裁");
        if(!Set.of("诗歌","散文","小说","随笔","其他").contains(genre)){if(customGenre.isBlank())customGenre=genre;genre="其他";}
        if(!genre.equals("其他"))customGenre="";
        ArticleLayout layout=input.layout()!=null?input.layout().validated():existing!=null&&Objects.equals(existing.get("genre"),genre)?ArticleLayout.decode(existing.get("bodyLayout"),genre):ArticleLayout.defaults(genre);
        String excerpt=bounded(input.excerpt(),240,"摘要");
        String raw=Objects.requireNonNullElse(input.body(),"");
        if(raw.length()>200000)throw error(400,"单篇正文请控制在 20 万字符以内");
        var document=WorkHtml.clean(raw);
        String body=document.body().html();
        Set<String> mediaIds=new HashSet<>();
        String cover=Objects.requireNonNullElse(input.cover(),"");
        List<String> sources=new ArrayList<>();document.select("img").forEach(img->sources.add(img.attr("src")));if(!cover.isBlank())sources.add(cover);
        for(String src:sources) {
            if(!src.matches(WorkHtml.MEDIA_PATH))throw error(400,"请使用平台上传的图片");
            String mediaId=src.substring(src.lastIndexOf('/')+1);
            if(db.count("SELECT COUNT(*) FROM media_files WHERE id=? AND owner_id=?",mediaId,author)==0)throw error(400,"图片不存在或不属于当前账号");
            mediaIds.add(mediaId);
        }
        String plain=document.text();
        if(input.publish() && (title.isBlank() || plain.isBlank()))throw error(400,"发布前请填写标题和正文");
        if(input.publish())checkText(title+" "+byline+" "+plain+" "+notes+" "+customGenre);
        if(input.collectionTags()!=null&&!input.collectionTags().isEmpty())throw error(400,"合集已改为独立属性，请刷新页面后选择一部合集");
        List<Long> tags=input.tagIds()==null?List.of():input.tagIds().stream().distinct().toList();
        if(tags.size()>5)throw error(400,"最多选择 5 个普通标签");
        for(Long tag:tags){var t=tag==null?null:db.one("SELECT kind FROM tags WHERE id=?",tag);if(t==null||!"TOPIC".equals(t.get("kind")))throw error(400,"请选择普通标签，合集请单独设置");}
        long currentCollection=existing!=null&&existing.get("collectionId") instanceof Number n?n.longValue():0;
        if(input.collectionId()!=null&&input.collectionId()!=currentCollection)permissions.require(username,"COLLECTIONS");
        if(excerpt.isBlank())excerpt=plain.substring(0,Math.min(plain.length(),160));
        String status=input.publish()?"PUBLISHED":"DRAFT";
        if(id==null)id=db.insert("INSERT INTO works(author_id,title,genre,excerpt,body,plain_text,cover,status,published_at,body_layout,custom_genre,pen_name_id,byline,source_note,notes) VALUES(?,?,?,?,?,?,?,?,IF(?='PUBLISHED',CURRENT_TIMESTAMP,NULL),?,?,?,?,?,?)",author,title,genre,excerpt,body,plain,cover,status,status,layout.encode(),customGenre,penNameId,byline,sourceNote,notes);
        else db.update("UPDATE works SET title=?,genre=?,excerpt=?,body=?,plain_text=?,cover=?,status=?,published_at=IF(?='PUBLISHED',COALESCE(published_at,CURRENT_TIMESTAMP),published_at),body_layout=?,custom_genre=?,pen_name_id=?,byline=?,source_note=?,notes=? WHERE id=?",title,genre,excerpt,body,plain,cover,status,status,layout.encode(),customGenre,penNameId,byline,sourceNote,notes,id);
        db.update("DELETE FROM work_tags WHERE work_id=?",id);
        for(long tag:tags)db.update("INSERT INTO work_tags(work_id,tag_id) VALUES(?,?)",id,tag);
        collections.assign(id,author,input.collectionId());
        db.update("DELETE FROM work_media WHERE work_id=?",id);
        for(String mid:mediaIds)db.update("INSERT INTO work_media(work_id,media_id) VALUES(?,?)",id,mid);
        return Map.of("id",id,"status",status);
    }
    public static String bounded(String value,int max,String label) {
        String s=Objects.requireNonNullElse(value,"").trim(); if(s.length()>max)throw error(400,label+"过长");return s;
    }
    public static void checkText(String value) {
        if(value.matches("(?s).*([\\p{L}\\p{N}])\\1{40,}.*"))throw error(400,"检测到大量重复字符，请整理正文后再发布");
        if(value.indexOf('\u0000')>=0)throw error(400,"文本含有无效字符");
    }
}
