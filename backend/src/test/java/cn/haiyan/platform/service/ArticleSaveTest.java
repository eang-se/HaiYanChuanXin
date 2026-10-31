package cn.haiyan.platform.service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleSaveTest {
    private final CommunityStore db=mock(CommunityStore.class);
    private final CommunityService service=spy(new CommunityService(db,mock(WorkCollections.class),mock(AccountPermissions.class),new PenNames(db)));
    private final String mediaId="12345678-1234-1234-1234-123456789abc",src="/api/media/"+mediaId;
    private CommunityService.WorkInput input(String body,boolean publish) {return new CommunityService.WorkInput("河岸","散文","",body,"",List.of(),List.of(),publish,"作者","","");}
    private void prepare(){doReturn(29L).when(service).memberId("editor");when(db.count("SELECT COUNT(*) FROM works WHERE id=? AND author_id=?",55L,29L)).thenReturn(1L);}
    @Test void savesCaptionAndFoldedTextIntoSearchExcerptAndMediaRelations(){
        prepare();when(db.count("SELECT COUNT(*) FROM media_files WHERE id=? AND owner_id=?",mediaId,29L)).thenReturn(1L);
        var captured=new AtomicReference<Object[]>();
        when(db.update(anyString(),any(Object[].class))).thenAnswer(i->{if(((String)i.getArgument(0)).startsWith("UPDATE works"))captured.set(i.getArguments());return 1;});
        service.save(55L,"editor",input("<figure data-size='narrow'><img src='"+src+"'><figcaption>河畔说明</figcaption></figure><details><summary>后记</summary><div data-disclosure-body><p>折叠中的文字</p></div></details>",true));
        var args=captured.get();assertTrue(((String)args[3]).contains("折叠中的文字"));assertTrue(((String)args[4]).contains("data-size=\"narrow\""));assertTrue(((String)args[5]).contains("河畔说明"));assertTrue(((String)args[5]).contains("后记"));
        verify(db).update("INSERT INTO work_media(work_id,media_id) VALUES(?,?)",55L,mediaId);
    }
    @Test void forgedOrUnownedImagesAreRejected(){
        prepare();for(String path:List.of("https://evil.test/a.jpg","data:image/png;base64,abc","/api/media/------------------------------------",src)) {
            assertThrows(ResponseStatusException.class,()->service.save(55L,"editor",input("<p>正文</p><img src='"+path+"'>",false)));
        }
        verify(db,never()).update(startsWith("UPDATE works"),any(Object[].class));
    }
    @Test void foldedTextCannotBypassPublicationReviewAndBodyLimit(){
        prepare();assertThrows(ResponseStatusException.class,()->service.save(55L,"editor",input("<details><summary>附录</summary><div data-disclosure-body><p>"+"字".repeat(41)+"</p></div></details>",true)));
        assertThrows(ResponseStatusException.class,()->service.save(55L,"editor",input("字".repeat(200001),false)));
    }
}
