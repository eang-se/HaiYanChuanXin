package cn.haiyan.platform.service;

import java.util.*;
import org.jsoup.nodes.Entities;

/** Validates a Tiptap document against the exact vocabulary accepted by WorkHtml. */
public final class WorkDocument {
    private WorkDocument() {}
    private static RuntimeException invalid() { return CommunityService.error(400,"文章结构与正文不一致，请刷新后重试"); }
    public static void validate(Map<String,Object> document, String safeHtml) {
        if (document == null) return; // Legacy clients and unconverted documents remain supported.
        if(!"doc".equals(document.get("type")))throw invalid();
        var rendered = new StringBuilder();
        render(document, rendered, 0, new int[]{0});
        if (rendered.length()>400000 || !WorkHtml.clean(rendered.toString()).body().html().equals(safeHtml)) throw invalid();
    }
    private static String esc(Object value) { return Entities.escape(String.valueOf(value)); }
    private static String attribute(Object value) { return esc(value).replace("\"","&quot;"); }
    @SuppressWarnings("unchecked")
    private static void render(Map<String,Object> node,StringBuilder out,int depth,int[] count) {
        if(depth>40 || ++count[0]>50000 || out.length()>400000) throw invalid();
        Object t=node.get("type"); if(!(t instanceof String type)) throw invalid();
        if(!Set.of("type","attrs","content","marks","text").containsAll(node.keySet())) throw invalid();
        Map<String,Object> attrs=node.get("attrs") instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();
        if(node.containsKey("attrs") && !(node.get("attrs") instanceof Map<?,?>))throw invalid();
        Object raw=node.get("content"); List<?> content=raw instanceof List<?> l?l:List.of();
        if(raw!=null && !(raw instanceof List<?>))throw invalid();
        if(type.equals("text")) {
            if(!(node.get("text") instanceof String text) || text.isEmpty() || !content.isEmpty() || !attrs.isEmpty())throw invalid();
            out.append(esc(text));return;
        }
        if(node.containsKey("text")||(node.containsKey("marks")&&!type.equals("hardBreak")))throw invalid();
        String tag=switch(type){case "doc"->"";case "paragraph"->"p";case "heading"-> {Object level=attrs.get("level"); if(!(level instanceof Number n)||!Set.of(2,3).contains(n.intValue()))throw invalid();yield "h"+n.intValue();}case "blockquote"->"blockquote";case "bulletList"->"ul";case "orderedList"->"ol";case "listItem"->"li";case "horizontalRule"->"hr";case "hardBreak"->"br";case "codeBlock"->"pre";case "image"->"figure";case "disclosure"->"details";case "disclosureTitle"->"summary";case "disclosureBody"->"div";default->throw invalid();};
        Set<String> allowed=switch(type){case "heading"->Set.of("level");case "orderedList"->Set.of("start","type");case "codeBlock"->Set.of("language");case "image"->Set.of("src","alt","width","height","size");default->Set.of();};
        if(!allowed.containsAll(attrs.keySet()))throw invalid();
        if(type.equals("orderedList")) {
            Object start=attrs.get("start"),listType=attrs.get("type");
            if(start!=null&&(!(start instanceof Number n)||n.intValue()<1||n.intValue()>999999||n.doubleValue()!=n.intValue()))throw invalid();
            if(listType!=null)throw invalid();
        }
        if(type.equals("codeBlock")&&attrs.get("language")!=null&&(!(attrs.get("language") instanceof String language)||!language.matches("[a-zA-Z0-9_+.-]{1,40}")))throw invalid();
        validateChildren(type,content);
        if(type.equals("doc")&&depth!=0)throw invalid();
        if(type.equals("horizontalRule")||type.equals("hardBreak")){if(!content.isEmpty())throw invalid();out.append('<').append(tag).append('>');return;}
        if(type.equals("image")) {
            Object src=attrs.get("src");if(!(src instanceof String s)||!s.matches(WorkHtml.MEDIA_PATH))throw invalid();
            Object size=attrs.getOrDefault("size","full");if(!Set.of("full","narrow").contains(size))throw invalid();
            if(attrs.get("alt")!=null&&!(attrs.get("alt") instanceof String))throw invalid();
            out.append("<figure data-size=\"").append(size).append("\"><img src=\"").append(attribute(src)).append("\" alt=\"").append(attribute(Objects.requireNonNullElse(attrs.get("alt"),""))).append('"');
            Object w=attrs.get("width"),h=attrs.get("height");if((w==null)!=(h==null))throw invalid();
            if(w!=null){if(!dimension(w)||!dimension(h))throw invalid();out.append(" width=\"").append(esc(w)).append("\" height=\"").append(esc(h)).append('"');}
            out.append("><figcaption>"); renderChildren(content,out,depth,count);out.append("</figcaption></figure>");return;
        }
        if(!tag.isEmpty()) {out.append('<').append(tag); if(type.equals("orderedList")&&attrs.get("start") instanceof Number n&&n.intValue()!=1)out.append(" start=\"").append(n.intValue()).append('"'); if(type.equals("disclosureBody"))out.append(" data-disclosure-body=\"\"");out.append('>');}
        if(type.equals("codeBlock"))out.append("<code>");
        renderChildren(content,out,depth,count);
        if(type.equals("codeBlock"))out.append("</code>");
        if(!tag.isEmpty())out.append("</").append(tag).append('>');
    }
    private static boolean dimension(Object value) {String s=String.valueOf(value);return s.matches("[1-9][0-9]{0,3}")&&Integer.parseInt(s)<=8000;}
    private static void validateChildren(String parent,List<?> content) {
        Set<String> allowed=switch(parent){
            case "doc","blockquote","disclosureBody" -> Set.of("paragraph","heading","blockquote","bulletList","orderedList","horizontalRule","codeBlock","image","disclosure");
            case "listItem" -> Set.of("paragraph","heading","blockquote","bulletList","orderedList","horizontalRule","codeBlock","image","disclosure");
            case "bulletList","orderedList" -> Set.of("listItem");
            case "paragraph","heading","image","disclosureTitle" -> Set.of("text","hardBreak");
            case "codeBlock" -> Set.of("text");
            case "disclosure" -> Set.of("disclosureTitle","disclosureBody");
            case "horizontalRule","hardBreak" -> Set.of();
            default -> throw invalid();
        };
        for(Object child:content)if(!(child instanceof Map<?,?> m)||!allowed.contains(m.get("type")))throw invalid();
        if(Set.of("doc","blockquote","disclosureBody","bulletList","orderedList","listItem").contains(parent)&&content.isEmpty())throw invalid();
        if(parent.equals("listItem")&&!"paragraph".equals(((Map<?,?>)content.getFirst()).get("type")))throw invalid();
        if(parent.equals("disclosure")&&(content.size()!=2||!"disclosureTitle".equals(((Map<?,?>)content.get(0)).get("type"))||!"disclosureBody".equals(((Map<?,?>)content.get(1)).get("type"))))throw invalid();
        if(parent.equals("codeBlock"))for(Object child:content)if(((Map<?,?>)child).containsKey("marks"))throw invalid();
    }
    @SuppressWarnings("unchecked") private static void renderChildren(List<?> content,StringBuilder out,int depth,int[] count) {
        List<String> open=new ArrayList<>();
        for(Object child:content){
            if(!(child instanceof Map<?,?>))throw invalid();Map<String,Object> node=(Map<String,Object>)child;
            List<String> next=marks(node.get("marks"));int common=0;
            while(common<open.size()&&common<next.size()&&open.get(common).equals(next.get(common)))common++;
            for(int i=open.size()-1;i>=common;i--)out.append("</").append(open.get(i)).append('>');
            for(int i=common;i<next.size();i++)out.append('<').append(next.get(i)).append('>');
            open=next;render(node,out,depth+1,count);
        }
        for(int i=open.size()-1;i>=0;i--)out.append("</").append(open.get(i)).append('>');
    }
    private static List<String> marks(Object value) {
        List<String> result=new ArrayList<>();if(value==null)return result;if(!(value instanceof List<?> list))throw invalid();
        for(Object item:list){if(!(item instanceof Map<?,?> mark)||mark.size()!=1||!(mark.get("type") instanceof String type))throw invalid();
            String tag=switch(type){case "bold"->"strong";case "italic"->"em";case "underline"->"u";case "strike"->"s";case "code"->"code";default->throw invalid();};
            if(result.contains(tag))throw invalid();result.add(tag);}
        return result;
    }
}
