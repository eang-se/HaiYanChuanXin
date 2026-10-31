package cn.haiyan.platform.service;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class RetiredAvatarCleanup implements ApplicationRunner {
    private static final Logger log=LoggerFactory.getLogger(RetiredAvatarCleanup.class);
    private final CommunityStore db;
    private final TransactionTemplate transactions;
    private final Path root;
    @org.springframework.beans.factory.annotation.Autowired
    public RetiredAvatarCleanup(CommunityStore db,TransactionTemplate transactions){this(db,transactions,Path.of("data","images").toAbsolutePath().normalize());}
    RetiredAvatarCleanup(CommunityStore db,TransactionTemplate transactions,Path root){this.db=db;this.transactions=transactions;this.root=root;}
    @Override public void run(ApplicationArguments args){
        for(var item:db.list("SELECT media_id FROM retired_avatar_media")) {
            String id=(String)item.get("mediaId");
            try { transactions.executeWithoutResult(status->clean(id)); }
            catch(Exception ex){log.warn("Could not remove a retired avatar; it will be retried on startup",ex);}
        }
    }
    void clean(String id){
        if(!id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))return;
        var file=db.one("SELECT file_path FROM media_files WHERE id=? FOR UPDATE",id);
        if(file==null){db.update("DELETE FROM retired_avatar_media WHERE media_id=?",id);return;}
        if(db.one("SELECT media_id FROM work_media WHERE media_id=? LIMIT 1 FOR UPDATE",id)!=null){
            db.update("DELETE FROM retired_avatar_media WHERE media_id=?",id);return;
        }
        if(!(id+".jpg").equals(file.get("filePath"))) {log.warn("Retired avatar has an unexpected file path; leaving it untouched");return;}
        Path target=root.resolve(id+".jpg").normalize();
        if(!target.startsWith(root))return;
        try{Files.deleteIfExists(target);}catch(java.io.IOException ex){throw new IllegalStateException("Cannot delete retired avatar",ex);}
        db.update("DELETE FROM retired_avatar_media WHERE media_id=?",id);
        db.update("DELETE FROM media_files WHERE id=?",id);
    }
}
