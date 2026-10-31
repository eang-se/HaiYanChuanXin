package cn.haiyan.platform.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

/** The persisted article vocabulary. Keep in sync with frontend/src/article/html.ts. */
public final class WorkHtml {
    private WorkHtml() {}
    public static final String MEDIA_PATH = "/api/media/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}";
    private static final Safelist ALLOWED = new Safelist()
        .addTags("p", "br", "strong", "em", "u", "s", "h2", "h3", "blockquote", "ul", "ol", "li", "hr", "img", "figure", "figcaption", "details", "summary", "div", "pre", "code")
        .addAttributes("img", "src", "alt", "width", "height")
        .addAttributes("figure", "data-size")
        .addAttributes("div", "data-disclosure-body")
        .addAttributes("ol", "start");

    public static Document clean(String raw) {
        var doc = new Cleaner(ALLOWED).clean(Jsoup.parseBodyFragment(raw));
        doc.outputSettings().prettyPrint(false);
        // Flatten nested disclosures, retaining both their title and their body as text blocks.
        for (var nested : doc.select("details details")) {
            for (var summary : nested.children()) if (summary.tagName().equals("summary")) summary.tagName("p");
            nested.unwrap();
        }
        for (var details : doc.select("details")) {
            Element title = details.children().stream().filter(e -> e.tagName().equals("summary")).findFirst().orElse(null);
            if (title == null) title = new Element("summary").text("折叠内容");
            else title.remove();
            inlineOnly(title);
            var body = new Element("div").attr("data-disclosure-body", "");
            for (var node : details.childNodesCopy()) body.appendChild(node);
            details.empty().appendChild(title).appendChild(body);
        }
        for (var div : doc.select("div")) {
            if (div.parent() != null && div.parent().tagName().equals("details")) div.attr("data-disclosure-body", "");
            else div.unwrap();
        }
        for (var summary : doc.select("summary")) {
            if (summary.parent() == null || !summary.parent().tagName().equals("details")) summary.tagName("p");
        }
        for (var figure : doc.select("figure")) {
            if (figure.select("img").size() != 1) { figure.unwrap(); continue; }
            figure.attr("data-size", "narrow".equals(figure.attr("data-size")) ? "narrow" : "full");
            var image = figure.selectFirst("img"); image.remove();
            var caption = new Element("figcaption");
            for (var node : figure.childNodesCopy()) caption.appendChild(node);
            inlineOnly(caption);
            figure.empty().appendChild(image).appendChild(caption);
        }
        for (var caption : doc.select("figcaption")) {
            if (caption.parent() == null || !caption.parent().tagName().equals("figure")) caption.tagName("p");
            else inlineOnly(caption);
        }
        for (var img : doc.select("img")) {
            for (var attr : new String[]{"width", "height"}) {
                if (!img.attr(attr).matches("[1-9][0-9]{0,3}") || Integer.parseInt(img.attr(attr)) > 8000) img.removeAttr(attr);
            }
            if (!img.hasAttr("width") || !img.hasAttr("height")) { img.removeAttr("width"); img.removeAttr("height"); }
        }
        for (var ol : doc.select("ol[start]")) if (!ol.attr("start").matches("[1-9][0-9]{0,5}")) ol.removeAttr("start");
        return doc;
    }

    private static void inlineOnly(Element parent) {
        for (var child : parent.select("*")) {
            if (child == parent) continue;
            if (!java.util.Set.of("br", "strong", "em", "u", "s", "code").contains(child.tagName())) child.unwrap();
        }
    }
}
