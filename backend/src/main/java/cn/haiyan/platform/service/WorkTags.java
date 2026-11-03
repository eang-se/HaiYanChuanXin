package cn.haiyan.platform.service;

import java.util.*;
import tools.jackson.databind.ObjectMapper;
import static cn.haiyan.platform.service.CommunityService.*;

/** Keeps participation in managed activities separate from author-written subject tags. */
public final class WorkTags {
    private final CommunityStore db;
    public WorkTags(CommunityStore db){this.db=db;}
    public List<Long> resolve(Long workId,List<Long> legacy,List<Long> activities,List<String> content){
        Set<Long> result=new LinkedHashSet<>();
        if(activities==null&&content==null){
            if(legacy!=null&&legacy.size()>20)throw error(400,"标签数量过多");
            int activityCount=0,contentCount=0;
            for(Long id:legacy==null?List.<Long>of():new LinkedHashSet<>(legacy)){
                var tag=id==null?null:db.one("SELECT kind,name,is_activity,activity_active FROM tags WHERE id=? FOR UPDATE",id);
                if(tag==null||!"TOPIC".equals(tag.get("kind")))throw error(400,"标签不存在，合集请单独设置");
                if("海燕精选".equals(tag.get("name"))){
                    if(workId==null||db.count("SELECT COUNT(*) FROM work_tags WHERE work_id=? AND tag_id=?",workId,id)==0)throw error(403,"海燕精选由编辑部在稿件管理中设置");
                    continue;
                }
                if(Boolean.TRUE.equals(tag.get("isActivity"))){available(workId,id,tag);activityCount++;}else contentCount++;
                result.add(id);
            }
            if(activityCount>5||contentCount>5)throw error(400,"活动与内容标签各最多 5 个");
            return new ArrayList<>(result);
        }
        if(legacy!=null&&!legacy.isEmpty())throw error(400,"请分别提交参与活动与内容标签");
        Set<Long> activityIds=new LinkedHashSet<>(activities==null?List.of():activities);
        if(activityIds.size()>5)throw error(400,"最多参与 5 个活动");
        for(Long id:activityIds){
            var tag=id==null?null:db.one("SELECT kind,name,is_activity,activity_active FROM tags WHERE id=? FOR UPDATE",id);
            if(tag==null||!"TOPIC".equals(tag.get("kind"))||!Boolean.TRUE.equals(tag.get("isActivity")))throw error(400,"请选择当前可用的活动");
            available(workId,id,tag);result.add(id);
        }
        Map<String,String> names=new LinkedHashMap<>();
        if(content!=null){
            if(content.size()>20)throw error(400,"内容标签数量过多");
            for(String value:content){
                String name=bounded(value,32,"内容标签").replaceFirst("^[#＃]+","").strip();
                if(name.isEmpty())throw error(400,"内容标签不能为空");
                name=bounded(name,30,"内容标签");
                if(name.matches("(?s).*[\\p{Cc}\\p{Cf}].*"))throw error(400,"内容标签含有无效字符");
                if("海燕精选".equals(name))throw error(403,"海燕精选由编辑部设置，请填写其他内容标签");
                checkText(name);names.putIfAbsent(name.toLowerCase(Locale.ROOT),name);
            }
        }
        if(names.size()>5)throw error(400,"最多添加 5 个内容标签");
        for(String name:names.values()){
            db.update("INSERT INTO tags(name,description,is_activity,kind) VALUES(?,'',FALSE,'TOPIC') ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)",name);
            var tag=db.one("SELECT id FROM tags WHERE name=? AND kind='TOPIC' AND is_activity=FALSE",name);
            result.add(((Number)tag.get("id")).longValue());
        }
        return new ArrayList<>(result);
    }
    private void available(Long workId,Long id,Map<String,Object> tag){
        if(Boolean.FALSE.equals(tag.get("activityActive"))&&(workId==null||(db.count("SELECT COUNT(*) FROM work_tags WHERE work_id=? AND tag_id=?",workId,id)==0&&!inSavedDraft(workId,id))))
            throw error(400,"该活动已结束，请从当前可用活动中重新选择");
    }
    private boolean inSavedDraft(long workId,long id){
        var row=db.one("SELECT payload FROM work_drafts WHERE work_id=?",workId);
        if(row==null||!(row.get("payload") instanceof String payload))return false;
        try{var draft=new ObjectMapper().readValue(payload,Map.class);return draft.get("tagIds") instanceof List<?> ids&&ids.stream().anyMatch(value->value instanceof Number n&&n.longValue()==id);}
        catch(RuntimeException exception){return false;}
    }
}
