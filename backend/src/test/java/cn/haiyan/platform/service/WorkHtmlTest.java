package cn.haiyan.platform.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkHtmlTest {
    private static final String SRC="/api/media/12345678-1234-1234-1234-123456789abc";
    @Test void newFormatsRoundTripAndContributeToPlainText() {
        String raw="<p>第一行<br>第二行</p><figure data-size='narrow'><img src='"+SRC+"' width='1200' height='800'><figcaption>暮色<strong>说明</strong></figcaption></figure><details><summary>后记</summary><div data-disclosure-body><p>折叠正文</p><h3>札记</h3></div></details>";
        var doc=WorkHtml.clean(raw);String body=doc.body().html();
        assertEquals(body,WorkHtml.clean(body).body().html());assertTrue(body.contains("<br>"));
        assertEquals("narrow",doc.selectFirst("figure").attr("data-size"));assertEquals("1200",doc.selectFirst("img").attr("width"));
        assertTrue(doc.text().contains("暮色说明"));assertTrue(doc.text().contains("后记"));assertTrue(doc.text().contains("折叠正文"));
    }
    @Test void preservesLegacyContentAndRejectsUnneededAttributes() {
        var doc=WorkHtml.clean("<p id='app' style='color:red' onclick='x()'>诗<br>行</p><img src='"+SRC+"' alt='旧图' onerror='x()' srcset='https://evil.test/x 2x'><script>alert(1)</script><iframe src='x'></iframe><figure data-size='giant'><img src='"+SRC+"' width='99999999' height='2'></figure>");
        String body=doc.body().html();assertFalse(body.matches("(?s).*(onclick|style=|id=|onerror|srcset|script|iframe).*"));
        assertTrue(body.contains("诗<br>行"));assertTrue(body.contains("旧图"));assertEquals("full",doc.selectFirst("figure").attr("data-size"));assertFalse(doc.selectFirst("figure img").hasAttr("width"));
    }
    @Test void nestedDisclosuresFlattenAndRemainEditable() {
        var doc=WorkHtml.clean("<details><summary>外层</summary><div data-disclosure-body><p>起</p><details><summary>内层</summary><div data-disclosure-body><p>终</p></div></details></div></details>");
        assertEquals(1,doc.select("details").size());assertEquals(1,doc.select("summary").size());
        assertTrue(doc.text().contains("内层"));assertTrue(doc.text().contains("终"));
        assertEquals(doc.body().html(),WorkHtml.clean(doc.body().html()).body().html());
    }
}
