package cn.haiyan.platform.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetiredAvatarCleanupTest {
    @TempDir Path root;
    private static final String ID="12345678-1234-1234-1234-123456789abc";
    @Test void preservesImageReferencedByAnyWork() throws Exception {
        var db=mock(CommunityStore.class);var file=root.resolve(ID+".jpg");Files.writeString(file,"image");
        when(db.one("SELECT file_path FROM media_files WHERE id=? FOR UPDATE",ID)).thenReturn(Map.of("filePath",ID+".jpg"));
        when(db.one("SELECT media_id FROM work_media WHERE media_id=? LIMIT 1 FOR UPDATE",ID)).thenReturn(Map.of("mediaId",ID));
        new RetiredAvatarCleanup(db,mock(TransactionTemplate.class),root).clean(ID);
        assertTrue(Files.exists(file));verify(db,never()).update("DELETE FROM media_files WHERE id=?",ID);
    }
    @Test void removesOnlyUnreferencedAvatar() throws Exception {
        var db=mock(CommunityStore.class);var file=root.resolve(ID+".jpg");Files.writeString(file,"image");
        when(db.one("SELECT file_path FROM media_files WHERE id=? FOR UPDATE",ID)).thenReturn(Map.of("filePath",ID+".jpg"));
        when(db.one("SELECT media_id FROM work_media WHERE media_id=? LIMIT 1 FOR UPDATE",ID)).thenReturn(null);
        new RetiredAvatarCleanup(db,mock(TransactionTemplate.class),root).clean(ID);
        assertFalse(Files.exists(file));verify(db).update("DELETE FROM media_files WHERE id=?",ID);
    }
    @Test void rejectsUnexpectedPath() throws Exception {
        var db=mock(CommunityStore.class);var file=root.resolve("other.jpg");Files.writeString(file,"image");
        when(db.one("SELECT file_path FROM media_files WHERE id=? FOR UPDATE",ID)).thenReturn(Map.of("filePath","other.jpg"));
        new RetiredAvatarCleanup(db,mock(TransactionTemplate.class),root).clean(ID);
        assertTrue(Files.exists(file));verify(db,never()).update("DELETE FROM media_files WHERE id=?",ID);
    }
}
