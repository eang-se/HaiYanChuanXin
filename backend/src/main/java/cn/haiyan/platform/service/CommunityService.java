package cn.haiyan.platform.service;

import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommunityService {
    private final CommunityStore db;
    private final CollectionTags collectionTags;private final AccountPermissions permissions;
    public CommunityService(CommunityStore db,CollectionTags collectionTags,AccountPermissions permissions) { this.db=db;this.collectionTags=collectionTags;this.permissions=permissions; }
    public CommunityStore store() { return db; }
    public static ResponseStatusException error(int code,String message) { return new ResponseStatusException(HttpStatus.valueOf(code),message); }
    public Map<String,Object> member(String username) {
        var m=db.one("SELECT id,username,pen_name,bio,role,avatar_url,signature,qq_number,profile_completed,profile_prompt_seen,created_at FROM members WHERE username=?",username);
        if(m==null) throw error(401,"请先登录");m.put("permissions",permissions.effective(((Number)m.get("id")).longValue(),(String)m.get("role"))); return m;
    }
    public long memberId(String username) { return ((Number)member(username).get("id")).longValue(); }
    public static final String WORK_SELECT="SELECT w.*,m.pen_name AS author_name,m.avatar_url AS author_avatar,(SELECT COUNT(*) FROM reactions r WHERE r.work_id=w.id AND r.kind='LIKE') AS likes,(SELECT COUNT(*) FROM comments c WHERE c.work_id=w.id AND c.visible=TRUE) AS comment_count FROM works w JOIN members m ON m.id=w.author_id ";
    public List<Map<String,Object>> decorate(List<Map<String,Object>> rows,boolean detail) {
        return decorate(rows,detail,null);
    }
    public List<Map<String,Object>> decorate(List<Map<String,Object>> rows,boolean detail,Long viewer) {
        for(var w:rows) {
            boolean own=viewer!=null&&((Number)w.get("authorId")).longValue()==viewer;
            w.put("tags",db.list("SELECT t.* FROM tags t JOIN work_tags wt ON wt.tag_id=t.id LEFT JOIN collections c ON c.id=t.collection_id WHERE wt.work_id=?"+(own?"":" AND (t.kind='TOPIC' OR c.published=TRUE)"),w.get("id")));
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
    public record WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,List<String> collectionTags,boolean publish) {
        public WorkInput(String title,String genre,String excerpt,String body,String cover,List<Long> tagIds,boolean publish){this(title,genre,excerpt,body,cover,tagIds,null,publish);}
    }
    @Transactional
    public Map<String,Object> save(Long id,String username,WorkInput input) {
        long author=memberId(username);
        permissions.require(username,"WRITE");
        if(id!=null)owner(id,author);
        String title=bounded(input.title(),100,"标题");
        String genre=bounded(input.genre(),30,"体裁");
        String excerpt=bounded(input.excerpt(),240,"摘要");
        String raw=Objects.requireNonNullElse(input.body(),"");
        if(raw.length()>200000)throw error(400,"单篇正文请控制在 20 万字符以内");
        Safelist allowed=new Safelist().addTags("p","br","strong","em","u","s","h2","h3","blockquote","ul","ol","li","hr","img").addAttributes("img","src","alt");
        String body=Jsoup.clean(raw,allowed);
        var document=Jsoup.parseBodyFragment(body);
        Set<String> mediaIds=new HashSet<>();
        String cover=Objects.requireNonNullElse(input.cover(),"");
        List<String> sources=new ArrayList<>();document.select("img").forEach(img->sources.add(img.attr("src")));if(!cover.isBlank())sources.add(cover);
        for(String src:sources) {
            if(!src.matches("/api/media/[a-f0-9-]{36}"))throw error(400,"请使用平台上传的图片");
            String mediaId=src.substring(src.lastIndexOf('/')+1);
            if(db.count("SELECT COUNT(*) FROM media_files WHERE id=? AND owner_id=?",mediaId,author)==0)throw error(400,"图片不存在或不属于当前账号");
            mediaIds.add(mediaId);
        }
        String plain=document.text();
        if(input.publish() && (title.isBlank() || plain.isBlank()))throw error(400,"发布前请填写标题和正文");
        if(input.publish())checkText(title+" "+plain);
        List<Long> tags=new ArrayList<>(input.tagIds()==null?List.of():input.tagIds().stream().distinct().toList());
        if(tags.size()>15)throw error(400,"标签过多");
        int topics=0,groups=0;
        for(Long tag:tags){var t=tag==null?null:db.one("SELECT kind,owner_id FROM tags WHERE id=?",tag);if(t==null)throw error(400,"标签不存在");if("COLLECTION".equals(t.get("kind"))){groups++;if(((Number)t.get("ownerId")).longValue()!=author)throw error(403,"只能使用自己的合集标签");}else topics++;}
        if(topics>5)throw error(400,"最多选择 5 个普通标签");
        if(input.collectionTags()!=null&&!input.collectionTags().isEmpty())permissions.require(username,"COLLECTIONS");
        tags.addAll(collectionTags.resolve(author,input.collectionTags(),input.publish(),genre));tags=tags.stream().distinct().toList();
        if(tags.size()-topics>10)throw error(400,"最多添加 10 个合集标签");
        if(excerpt.isBlank())excerpt=plain.substring(0,Math.min(plain.length(),160));
        String status=input.publish()?"PUBLISHED":"DRAFT";
        if(id==null)id=db.insert("INSERT INTO works(author_id,title,genre,excerpt,body,plain_text,cover,status,published_at) VALUES(?,?,?,?,?,?,?,?,IF(?='PUBLISHED',CURRENT_TIMESTAMP,NULL))",author,title,genre.isBlank()?"其他":genre,excerpt,body,plain,cover,status,status);
        else db.update("UPDATE works SET title=?,genre=?,excerpt=?,body=?,plain_text=?,cover=?,status=?,published_at=IF(?='PUBLISHED',COALESCE(published_at,CURRENT_TIMESTAMP),published_at) WHERE id=?",title,genre,excerpt,body,plain,cover,status,status,id);
        collectionTags.attach(id,tags);
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
