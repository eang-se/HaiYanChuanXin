package cn.haiyan.platform.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class WorkDocumentTest {
    private static Map<String,Object> n(String type,Object... children) {return Map.of("type",type,"content",List.of(children));}
    private static Map<String,Object> text(String text) {return Map.of("type","text","text",text);}
    private static void accepts(Map<String,Object> json,String html) {assertDoesNotThrow(()->WorkDocument.validate(json,WorkHtml.clean(html).body().html()));}
    @Test void emptyParagraphsAndHardBreaks(){accepts(n("doc",n("paragraph"),n("paragraph",Map.of("type","hardBreak"))),"<p></p><p><br></p>");}
    @Test void listsMarksAndCode(){
        var marked=Map.of("type","text","text","河岸","marks",List.of(Map.of("type","bold"),Map.of("type","italic")));
        var list=Map.<String,Object>of("type","orderedList","attrs",Map.of("start",3),"content",List.of(n("listItem",n("paragraph",marked))));
        accepts(n("doc",list,n("codeBlock",text("a<b"))),"<ol start=\"3\"><li><p><strong><em>河岸</em></strong></p></li></ol><pre><code>a&lt;b</code></pre>");
    }
    @Test void adjacentTextKeepsSharedMarksOpen(){
        var bold=List.of(Map.of("type","bold"));var both=List.of(Map.of("type","bold"),Map.of("type","italic"));
        var a=Map.of("type","text","text","A","marks",bold);var b=Map.of("type","text","text","B","marks",both);var c=Map.of("type","text","text","C","marks",bold);
        accepts(n("doc",n("paragraph",a,b,c)),"<p><strong>A<em>B</em>C</strong></p>");
    }
    @Test void imageCaptionAndDisclosure(){
        var img=Map.<String,Object>of("type","image","attrs",Map.of("src","/api/media/12345678-1234-1234-1234-123456789abc","alt","河","width","1200","height","800","size","narrow"),"content",List.of(text("说明")));
        var fold=n("disclosure",n("disclosureTitle",text("标题")),n("disclosureBody",n("paragraph",text("内容"))));
        accepts(n("doc",img,fold),"<figure data-size=\"narrow\"><img src=\"/api/media/12345678-1234-1234-1234-123456789abc\" alt=\"河\" width=\"1200\" height=\"800\"><figcaption>说明</figcaption></figure><details><summary>标题</summary><div data-disclosure-body=\"\"><p>内容</p></div></details>");
    }
    @Test void rejectsMismatchInvalidNodeOrUnownedFormat(){
        assertThrows(ResponseStatusException.class,()->WorkDocument.validate(n("doc",n("paragraph",text("另一个正文"))),"<p>正文</p>"));
        assertThrows(ResponseStatusException.class,()->WorkDocument.validate(n("paragraph",text("正文")),"<p>正文</p>"));
        assertThrows(ResponseStatusException.class,()->WorkDocument.validate(n("doc",n("paragraph",n("heading",text("内容")))),"<p>内容</p>"));
        assertThrows(ResponseStatusException.class,()->WorkDocument.validate(n("doc",Map.of("type","image","attrs",Map.of("src","https://evil.test/x"))),""));
    }
}
