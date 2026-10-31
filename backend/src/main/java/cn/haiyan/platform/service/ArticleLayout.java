package cn.haiyan.platform.service;

import java.util.Set;

/** Bounded presentation choices; never accept author-provided CSS. */
public record ArticleLayout(String bodyAlign,String lineSpacing,String paragraphSpacing,String headingAlign,boolean indent) {
    public static ArticleLayout defaults(String genre) {
        return switch(genre) {
            case "诗歌" -> new ArticleLayout("left","airy","compact","left",false);
            case "小说" -> new ArticleLayout("left","relaxed","normal","center",true);
            case "散文" -> new ArticleLayout("left","relaxed","loose","left",false);
            case "随笔" -> new ArticleLayout("left","normal","normal","left",false);
            default -> new ArticleLayout("left","relaxed","normal","left",false);
        };
    }
    public ArticleLayout validated() {
        if(!Set.of("left","center").contains(java.util.Objects.requireNonNullElse(bodyAlign,"")) ||
           !Set.of("left","center").contains(java.util.Objects.requireNonNullElse(headingAlign,"")) ||
           !Set.of("normal","relaxed","airy").contains(java.util.Objects.requireNonNullElse(lineSpacing,"")) ||
           !Set.of("compact","normal","loose").contains(java.util.Objects.requireNonNullElse(paragraphSpacing,"")))
            throw CommunityService.error(400,"版式选项无效，请重新选择");
        return new ArticleLayout(bodyAlign,lineSpacing,paragraphSpacing,headingAlign,indent&&bodyAlign.equals("left"));
    }
    public String encode(){var layout=validated();return String.join(",",layout.bodyAlign,layout.lineSpacing,layout.paragraphSpacing,layout.headingAlign,String.valueOf(layout.indent));}
    public static ArticleLayout decode(Object stored,String genre){
        if(stored instanceof String value){
            String[] parts=value.split(",",-1);
            if(parts.length==5&&Set.of("true","false").contains(parts[4]))try{return new ArticleLayout(parts[0],parts[1],parts[2],parts[3],Boolean.parseBoolean(parts[4])).validated();}catch(org.springframework.web.server.ResponseStatusException ignored){}
        }
        return defaults(genre);
    }
}
