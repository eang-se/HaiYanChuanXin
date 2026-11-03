package cn.haiyan.platform.service;

import java.util.*;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A moderation response contains positions only, never matched words or text excerpts. */
public final class ArticleModerationWarning extends ResponseStatusException {
    public record Location(String field,Integer block,String tag) {}
    private final List<Location> locations;
    private final List<String> structure;
    public ArticleModerationWarning(List<Location> locations,Document document){
        super(HttpStatus.UNPROCESSABLE_ENTITY,"内容检查提示：疑似敏感或广告内容达到检查阈值。请修改后重试，或提交人工复核。");
        this.locations=List.copyOf(locations);
        this.structure=document.body().children().stream().map(e->e.normalName()).toList();
    }
    public List<Location> locations(){return locations;}
    public List<String> structure(){return structure;}
    public static List<Location> locate(Document document,Map<String,String> fields){
        var found=new ArrayList<Location>();
        fields.forEach((name,value)->{if(!ArticleModeration.checkMatch(value).isEmpty())found.add(new Location(name,null,null));});
        var children=document.body().children();
        boolean bodyHit=false;
        for(int i=0;i<children.size();i++){
            var block=children.get(i);
            if(!ArticleModeration.checkMatch(block.text()).isEmpty()){
                bodyHit=true;found.add(new Location("body",i+1,block.normalName()));
            }
        }
        if(!bodyHit&&!ArticleModeration.checkMatch(document.text()).isEmpty())found.add(new Location("body",null,null));
        return found;
    }
}
