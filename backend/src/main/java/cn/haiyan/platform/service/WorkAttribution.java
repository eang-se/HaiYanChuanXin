package cn.haiyan.platform.service;

import java.util.*;
import static cn.haiyan.platform.service.CommunityService.*;

/** Public credit is independent of the account that owns and moderates the manuscript. */
public final class WorkAttribution {
    public record Credit(String mode,String byline,Long penNameId){}
    public static Credit resolve(PenNames pens,long author,String requested,String byline,Long penNameId,Map<String,Object> existing){
        String mode=requested==null?"LEGACY":requested;
        if(!Set.of("LEGACY","BOUND","ANONYMOUS","TEMPORARY").contains(mode))throw error(400,"请选择有效的署名方式");
        if(mode.equals("ANONYMOUS"))return new Credit(mode,"佚名",null);
        if(mode.equals("TEMPORARY")){
            String name=bounded(byline,120,"临时署名");
            if(name.isBlank()||Set.of("佚名","署名待核").contains(name))throw error(400,"请填写临时署名，或选择佚名");
            checkText(name);
            return new Credit(mode,name,null);
        }
        var pen=pens.resolve(author,penNameId,existing);
        if(mode.equals("BOUND")&&pen==null)throw error(400,"请选择已经绑定的笔名");
        return new Credit(mode,pen==null?byline:(String)pen.get("name"),pen==null?null:((Number)pen.get("id")).longValue());
    }
}
