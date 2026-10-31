package cn.haiyan.platform.service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleLayoutTest {
    private final CommunityStore db=mock(CommunityStore.class);
    private final CommunityService service=spy(new CommunityService(db,mock(WorkCollections.class),mock(AccountPermissions.class),new PenNames(db)));
    private final AtomicReference<Object[]> saved=new AtomicReference<>();
    private final Map<String,Object> existing=new HashMap<>(Map.of("byline","作者","sourceNote","","notes","","genre","其他","customGenre","童话","bodyLayout","center,airy,compact,center,false"));
    private void prepare(){
        doReturn(29L).when(service).memberId("editor");
        when(db.count("SELECT COUNT(*) FROM works WHERE id=? AND author_id=?",55L,29L)).thenReturn(1L);
        when(db.one("SELECT byline,source_note,notes,genre,custom_genre,body_layout,pen_name_id,collection_id FROM works WHERE id=?",55L)).thenReturn(existing);
        when(db.update(anyString(),any(Object[].class))).thenAnswer(i->{if(((String)i.getArgument(0)).startsWith("UPDATE works"))saved.set(i.getArguments());return 1;});
    }
    private CommunityService.WorkInput input(String genre,String custom,ArticleLayout layout){return new CommunityService.WorkInput("作品",genre,"","<h2>第一节</h2><p>正文<br>下一行</p>","",List.of(),List.of(),false,"作者","","",custom,layout);}
    @Test void genreDefaultsAndPresentationRoundTrip(){
        assertEquals("airy",ArticleLayout.defaults("诗歌").lineSpacing());assertEquals("compact",ArticleLayout.defaults("诗歌").paragraphSpacing());
        assertEquals("center",ArticleLayout.defaults("小说").headingAlign());assertTrue(ArticleLayout.defaults("小说").indent());
        assertEquals(new ArticleLayout("left","relaxed","normal","left",false),ArticleLayout.defaults("其他"));
        var custom=new ArticleLayout("center","airy","compact","center",false);
        assertEquals(custom,ArticleLayout.decode(custom.encode(),"其他"));
    }
    @Test void savesCustomGenreAndLayoutSeparatelyFromHtml(){
        prepare();var custom=new ArticleLayout("center","airy","compact","center",false);
        service.save(55L,"editor",input("其他","童话",custom));
        assertEquals(custom.encode(),saved.get()[9]);assertEquals("童话",saved.get()[10]);assertTrue(((String)saved.get()[4]).contains("<br>"));
    }
    @Test void oldClientsRetainSavedChoicesWhileChangingGenreGetsTheNewDefault(){
        prepare();service.save(55L,"editor",input("其他",null,null));assertEquals(existing.get("bodyLayout"),saved.get()[9]);assertEquals("童话",saved.get()[10]);
        service.save(55L,"editor",input("小说",null,null));assertEquals(ArticleLayout.defaults("小说").encode(),saved.get()[9]);assertEquals("",saved.get()[10]);
    }
    @Test void invalidCssChoicesAndOverlongNamesCannotBeSaved(){
        prepare();assertThrows(ResponseStatusException.class,()->service.save(55L,"editor",input("其他","字".repeat(31),null)));
        assertThrows(ResponseStatusException.class,()->service.save(55L,"editor",input("其他","",new ArticleLayout("left","url(evil)","normal","left",false))));
        assertNull(saved.get());assertEquals(ArticleLayout.defaults("诗歌"),ArticleLayout.decode("position:fixed","诗歌"));
    }
    @Test void legacyFreeformGenreCanBeEditedUnderOther(){
        prepare();service.save(55L,"editor",input("书信","",null));assertEquals("其他",saved.get()[2]);assertEquals("书信",saved.get()[10]);
    }
}
