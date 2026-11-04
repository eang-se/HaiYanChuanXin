package cn.haiyan.platform.service;

import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.allow.WordAllows;
import com.github.houbb.sensitive.word.support.deny.WordDenys;
import com.github.houbb.sensitive.word.support.result.WordResultHandlers;
import com.github.houbb.sensitive.word.api.IWordResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Local, open-source word screening. A match is a signal, not a verdict. */
@Component
public final class ArticleModeration {
    private static final Set<String> ARTICLE_EXCLUSIONS = articleExclusions();
    private static final Set<String> ARTICLE_CATEGORIES = articleCategoryWords();
    private static final Pattern ARTICLE_URL = Pattern.compile("(?i)(?:(?:https?://|www\\.)[a-z0-9][a-z0-9.-]*|(?<![a-z0-9_-])[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)*\\.(?:com|cn|org|net|edu|gov|io)(?![a-z0-9-]))(?::\\d{1,5})?(?:/[a-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]*)?");
    // The upstream dictionary includes uncategorized religious terms and insults.
    private static final SensitiveWordBs UPSTREAM = SensitiveWordBs.newInstance().init();
    // Preserve the full span of a direct insult that upstream matches only partially.
    private static final SensitiveWordBs DIRECT = SensitiveWordBs.newInstance()
        .wordDeny(()->List.of("操你妈")).wordAllow(WordAllows.empty()).init();
    private static final SensitiveWordBs ADVERTISING = SensitiveWordBs.newInstance()
        .wordDeny(ArticleModeration::advertisingWords).wordAllow(WordAllows.empty()).init();

    private static final SensitiveWordBs ARTICLE_UPSTREAM = SensitiveWordBs.newInstance()
        .wordDeny(()->Stream.concat(WordDenys.defaults().deny().stream().filter(ArticleModeration::includedInArticles),Stream.of("兼职")).distinct().toList()).init();

    public ArticleModeration() {} // Spring initializes dictionaries at startup, before publication requests.
    private static Set<String> articleExclusions(){
        try(var stream=ArticleModeration.class.getResourceAsStream("/moderation/article-exclusions.txt")){
            if(stream==null)throw new IllegalStateException("缺少文章排除词表");
            try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
                Set<String> exclusions=new HashSet<>();
                reader.lines().map(String::trim).filter(line->!line.isEmpty()&&!line.startsWith("#")).map(ArticleModeration::normalize).forEach(line->{
                    int[] cps=line.codePoints().toArray();
                    for(int i=0;i<cps.length;i++)for(int j=i+2;j<=cps.length;j++)exclusions.add(new String(cps,i,j-i));
                });
                return Set.copyOf(exclusions);
            }
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private static Set<String> articleCategoryWords(){
        try(var stream=ArticleModeration.class.getResourceAsStream("/sensitive_word_tags.txt")){
            if(stream==null)throw new IllegalStateException("缺少开源词库分类");
            try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
                Set<String> words=new HashSet<>();
                reader.lines().forEach(line->{
                    int split=line.lastIndexOf(' ');if(split<=0)return;
                    Set<String> tags=new HashSet<>(Arrays.asList(line.substring(split+1).trim().split(",")));
                    if(!tags.contains("1")&&(tags.contains("0")||tags.contains("2")||tags.contains("3")))words.add(normalize(line.substring(0,split)));
                });
                return Set.copyOf(words);
            }
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private static boolean includedInArticles(String word){String normalized=normalize(word);return ARTICLE_CATEGORIES.contains(normalized)&&!ARTICLE_EXCLUSIONS.contains(normalized);}
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
    /** Flag at least three separate hits covering 10%, or any hits covering 50%, of non-space characters. */
    public static List<String> check(String value){
        String text=normalize(value);long length=visibleCharacters(text);
        if(length==0)return List.of();
        List<Span> hits=new ArrayList<>();
        for(SensitiveWordBs source:List.of(ARTICLE_UPSTREAM)){
            for(IWordResult match:source.findAll(text,WordResultHandlers.raw())){
                int start=match.startIndex(),end=match.endIndex();
                if(start>=0&&end>start&&end<=text.length())hits.add(new Span(start,end));
            }
        }
        var links=ARTICLE_URL.matcher(text);while(links.find())hits.add(new Span(links.start(),links.end()));
        hits.sort(Comparator.comparingInt(Span::start).thenComparingInt(Span::end));
        int groups=0,start=-1,end=-1;long covered=0;
        for(Span hit:hits){
            if(start<0){start=hit.start();end=hit.end();}
            else if(hit.start()<=end)end=Math.max(end,hit.end());
            else {groups++;covered+=visibleCharacters(text.substring(start,end));start=hit.start();end=hit.end();}
        }
        if(start>=0){groups++;covered+=visibleCharacters(text.substring(start,end));}
        return (groups>=3&&covered*100>=length*10)||covered*100>=length*50?List.of("开源词库检测到疑似敏感内容密度较高"):List.of();
    }
    /** Single-hit checks are retained for comments and for locating blocks after an article is flagged. */
    public static List<String> checkComment(String value){return checkMatch(value);}
    public static List<String> checkArticleMatch(String value){
        String text=normalize(value);var findings=new ArrayList<String>();
        if(ARTICLE_UPSTREAM.contains(text))findings.add("开源词库检测到疑似敏感内容");
        if(ARTICLE_URL.matcher(text).find())findings.add("检测到链接");
        return findings;
    }
    public static List<String> checkMatch(String value){
        String text=normalize(value);var findings=new ArrayList<String>();
        if(DIRECT.contains(text))findings.add("开源词库检测到疑似敏感内容");
        else if(UPSTREAM.contains(text))findings.add("开源词库检测到疑似敏感内容");
        if(ADVERTISING.contains(text))findings.add("开源词库检测到疑似广告内容");
        return findings;
    }
}
