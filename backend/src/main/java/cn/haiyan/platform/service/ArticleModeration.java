package cn.haiyan.platform.service;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.allow.WordAllows;
import com.github.houbb.sensitive.word.support.result.WordResultHandlers;
import com.github.houbb.sensitive.word.api.IWordResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import org.springframework.stereotype.Component;

/** Local, open-source word screening. A match is a signal, not a verdict. */
@Component
public final class ArticleModeration {
    // The upstream dictionary includes uncategorized religious terms and insults.
    private static final SensitiveWordBs UPSTREAM = SensitiveWordBs.newInstance().init();
    private static final SensitiveWordBs ADVERTISING = SensitiveWordBs.newInstance()
        .wordDeny(ArticleModeration::advertisingWords).wordAllow(WordAllows.empty()).init();

    public ArticleModeration() {} // Spring initializes dictionaries at startup, before publication requests.
    private static List<String> advertisingWords() {
        try(var stream=ArticleModeration.class.getResourceAsStream("/moderation/upstream-ads.txt")){
            if(stream==null)throw new IllegalStateException("缺少广告词库");
            try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
                return reader.lines().map(String::trim).filter(s->s.codePointCount(0,s.length())>=4).distinct().toList();
            }
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private static String normalize(String value){return Normalizer.normalize(Objects.requireNonNullElse(value,""),Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
    private static long visibleCharacters(String text){return text.codePoints().filter(c->!Character.isWhitespace(c)&&!Character.isSpaceChar(c)).count();}
    private record Span(int start,int end) {}
    /** At least three separate hits covering at least 1% of non-space characters. */
    public static List<String> check(String value){
        String text=normalize(value);long length=visibleCharacters(text);
        if(length==0)return List.of();
        List<Span> hits=new ArrayList<>();
        for(SensitiveWordBs source:List.of(UPSTREAM,ADVERTISING)){
            for(IWordResult match:source.findAll(text,WordResultHandlers.raw())){
                int start=match.startIndex(),end=match.endIndex();
                if(start>=0&&end>start&&end<=text.length())hits.add(new Span(start,end));
            }
        }
        hits.sort(Comparator.comparingInt(Span::start).thenComparingInt(Span::end));
        int groups=0,start=-1,end=-1;long covered=0;
        for(Span hit:hits){
            if(start<0){start=hit.start();end=hit.end();}
            else if(hit.start()<=end)end=Math.max(end,hit.end());
            else {groups++;covered+=visibleCharacters(text.substring(start,end));start=hit.start();end=hit.end();}
        }
        if(start>=0){groups++;covered+=visibleCharacters(text.substring(start,end));}
        return groups>=3&&covered*100>=length?List.of("开源词库检测到疑似敏感内容密度较高"):List.of();
    }
    /** Single-hit checks are retained for comments and for locating blocks after an article is flagged. */
    public static List<String> checkComment(String value){return checkMatch(value);}
    public static List<String> checkMatch(String value){
        String text=normalize(value);var findings=new ArrayList<String>();
        if(UPSTREAM.contains(text))findings.add("开源词库检测到疑似敏感内容");
        if(ADVERTISING.contains(text))findings.add("开源词库检测到疑似广告内容");
        return findings;
    }
}
