package cn.haiyan.platform.service;

import cn.haiyan.platform.web.UploadController;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArticleMediaTest {
    private final CommunityService service=mock(CommunityService.class);
    private final CommunityStore store=mock(CommunityStore.class);
    private final UploadController uploads=new UploadController(service,mock(AccountPermissions.class));
    @Test void serverRejectsDisguisedFilesOversizedFilesAndDimensions() throws Exception {
        assertThrows(ResponseStatusException.class,()->uploads.image(new MockMultipartFile("file","fake.png","image/png",new byte[]{1,2,3}),()->"author"));
        assertThrows(ResponseStatusException.class,()->uploads.image(new MockMultipartFile("file","large.png","image/png",new byte[5*1024*1024+1]),()->"author"));
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(8001,1,BufferedImage.TYPE_INT_RGB),"png",bytes);
        assertThrows(ResponseStatusException.class,()->uploads.image(new MockMultipartFile("file","wide.png","image/png",bytes.toByteArray()),()->"author"));
        bytes.reset();ImageIO.write(new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB),"gif",bytes);
        assertThrows(ResponseStatusException.class,()->uploads.image(new MockMultipartFile("file","disguised.png","image/png",bytes.toByteArray()),()->"author"));
        verifyNoInteractions(service);
    }
    @Test void unpublishedMediaRequiresOwnershipAndPublicMediaKeepsNoStoreCache() {
        String id="12345678-1234-1234-1234-123456789abc";
        when(service.store()).thenReturn(store);
        when(store.one("SELECT * FROM media_files WHERE id=?",id)).thenReturn(Map.of("ownerId",29L,"filePath",id+".jpg"));
        assertThrows(ResponseStatusException.class,()->uploads.media(id,null));
        when(service.member("other")).thenReturn(Map.of("id",30L));assertThrows(ResponseStatusException.class,()->uploads.media(id,()->"other"));
        when(service.member("author")).thenReturn(Map.of("id",29L));assertEquals(200,uploads.media(id,()->"author").getStatusCode().value());
        when(store.count("SELECT COUNT(*) FROM work_media wm JOIN works w ON w.id=wm.work_id WHERE wm.media_id=? AND w.status='PUBLISHED'",id)).thenReturn(1L);
        var response=uploads.media(id,null);assertEquals(200,response.getStatusCode().value());assertEquals("no-store",response.getHeaders().getCacheControl());
    }
}
