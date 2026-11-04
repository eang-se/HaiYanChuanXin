package cn.haiyan.platform.service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleSaveTest {
    @Test void announcementIsReservedForPersonalAdministrators(){
        prepare();var data=new CommunityService.WorkInput("公告","公告","","<p>社团公告内容</p>","",List.of(),List.of(),true,"佚名","","");
        var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
        try{
            assertThrows(ResponseStatusException.class,()->service.save(55L,"editor",data));
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("operator",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PUBLIC_STAFF"))));
            assertEquals(403,assertThrows(ResponseStatusException.class,()->service.save(55L,"editor",data)).getStatusCode().value());
            for(String role:List.of("ADMIN","SUPER_ADMIN")){
                context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("operator",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+role))));
                assertDoesNotThrow(()->service.save(55L,"editor",data));
            }
        }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    private final CommunityStore db=mock(CommunityStore.class);
    private final CommunityService service=spy(new CommunityService(db,mock(WorkCollections.class),mock(AccountPermissions.class),new PenNames(db)));
    private final String mediaId="12345678-1234-1234-1234-123456789abc",src="/api/media/"+mediaId;
    private CommunityService.WorkInput input(String body,boolean publish) {return new CommunityService.WorkInput("河岸","散文","",body,"",List.of(),List.of(),publish,"作者","","");}
    private void prepare(){doReturn(29L).when(service).memberId("editor");when(db.count("SELECT COUNT(*) FROM works WHERE id=? AND author_id=?",55L,29L)).thenReturn(1L);}
    @Test void saveIsRejectedWhenAccountWasDeletedWhileWaitingForOwnerLock(){
        prepare();when(db.count("SELECT COUNT(*) FROM members WHERE id=? AND deleted_at IS NOT NULL",29L)).thenReturn(1L);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.save(55L,"editor",input("<p>正文</p>",true))).getStatusCode().value());
        verify(db,never()).update(startsWith("UPDATE works"),any(Object[].class));
    }
    @Test void savesCaptionAndFoldedTextIntoSearchExcerptAndMediaRelations(){
        prepare();when(db.count("SELECT COUNT(*) FROM media_files WHERE id=? AND owner_id=?",mediaId,29L)).thenReturn(1L);
        var captured=new AtomicReference<Object[]>();
        when(db.update(anyString(),any(Object[].class))).thenAnswer(i->{if(((String)i.getArgument(0)).startsWith("UPDATE works SET title="))captured.set(i.getArguments());return 1;});
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
        prepare();assertThrows(ResponseStatusException.class,()->service.save(55L,"editor",input("<details><summary>附录</summary><div data-disclosure-body><p>"+"博彩、博彩、博彩"+"</p></div></details>",true)));
        assertThrows(ResponseStatusException.class,()->service.save(55L,"editor",input("字".repeat(200001),false)));
    }
    @Test void flaggedPublicationIsBlockedBeforeWritesWhileDraftRemainsSavable(){
        prepare();var warning=assertThrows(ArticleModerationWarning.class,()->service.save(55L,"editor",input("<p>博彩、博彩、博彩</p>",true)));
        assertEquals(422,warning.getStatusCode().value());
        verify(db,never()).update(startsWith("UPDATE works"),any(Object[].class));
        assertDoesNotThrow(()->service.save(55L,"editor",input("<p>博彩、博彩、博彩</p>",false)));
    }
    @Test void oneHighDensityInsultBlocksPublicationButNotDraftSaving(){
        prepare();var warning=assertThrows(ArticleModerationWarning.class,()->service.save(55L,"editor",input("<p>操你妈</p>",true)));
        assertEquals(422,warning.getStatusCode().value());
        verify(db,never()).update(startsWith("UPDATE works"),any(Object[].class));
        assertDoesNotThrow(()->service.save(55L,"editor",input("<p>操你妈</p>",false)));
    }
    @Test void totalImageLimitAppliesToDraftsAndPublishingBeforeAnyWrites(){
        prepare();when(db.count("SELECT COUNT(*) FROM media_files WHERE id=? AND owner_id=?",mediaId,29L)).thenReturn(1L);
        when(db.count("SELECT COALESCE(SUM(size_bytes),0) FROM media_files WHERE id IN (?)",mediaId)).thenReturn(5L*1024*1024+1);
        for(boolean publish:List.of(false,true)){
            var ex=assertThrows(ResponseStatusException.class,()->service.save(55L,"editor",input("<p>正文</p><img src='"+src+"'>",publish)));
            assertTrue(ex.getReason().contains("5 MB"));
        }
        verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void exactImageBudgetIsAllowedAndRepeatedImageAndCoverAreCountedOnce(){
        prepare();when(db.count("SELECT COUNT(*) FROM media_files WHERE id=? AND owner_id=?",mediaId,29L)).thenReturn(1L);
        when(db.count("SELECT COALESCE(SUM(size_bytes),0) FROM media_files WHERE id IN (?)",mediaId)).thenReturn(5L*1024*1024);
        var data=new CommunityService.WorkInput("河岸","散文","","<p>正文</p><img src='"+src+"'><img src='"+src+"'>",src,List.of(),List.of(),false,"作者","","");
        assertDoesNotThrow(()->service.save(55L,"editor",data));
        verify(db).count("SELECT COALESCE(SUM(size_bytes),0) FROM media_files WHERE id IN (?)",mediaId);
        verify(db).update("INSERT INTO work_media(work_id,media_id) VALUES(?,?)",55L,mediaId);
    }
}
