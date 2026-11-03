package cn.haiyan.platform.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EditorialTagTest {
    private final CommunityStore db=mock(CommunityStore.class);
    private final CommunityService service=spy(new CommunityService(db,mock(WorkCollections.class),mock(AccountPermissions.class),new PenNames(db)));
    private CommunityService.WorkInput input(List<Long> tags) {
        return new CommunityService.WorkInput("新来信","散文","","<p>风经过校园，留下一个新的故事。</p>","",tags,true);
    }
    private void prepare() {
        doReturn(29L).when(service).memberId("author");
        when(db.count("SELECT COUNT(*) FROM works WHERE id=? AND author_id=?",55L,29L)).thenReturn(1L);
        when(db.one("SELECT kind,name,is_activity,activity_active FROM tags WHERE id=? FOR UPDATE",7L)).thenReturn(Map.of("kind","TOPIC","name","海燕精选"));
    }
    @Test void authorCannotAwardTheirOwnWorkEvenThroughForgedTagInput() {
        prepare();var error=assertThrows(ResponseStatusException.class,()->service.save(55L,"author",input(List.of(7L))));
        assertEquals(403,error.getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->service.save(null,"author",input(List.of(7L))));
        verify(db,never()).update(org.mockito.ArgumentMatchers.startsWith("UPDATE works"),org.mockito.ArgumentMatchers.any(Object[].class));
    }
    @Test void ordinaryAuthorSavePreservesEditorialTagWhetherSentOrOmitted() {
        prepare();when(db.count("SELECT COUNT(*) FROM work_tags WHERE work_id=? AND tag_id=?",55L,7L)).thenReturn(1L);
        service.save(55L,"author",input(List.of(7L)));
        service.save(55L,"author",input(List.of()));
        verify(db,times(2)).update("DELETE wt FROM work_tags wt JOIN tags t ON t.id=wt.tag_id WHERE wt.work_id=? AND t.name<>'海燕精选'",55L);
        verify(db,never()).update("DELETE FROM work_tags WHERE work_id=?",55L);
        verify(db,never()).update("INSERT INTO work_tags(work_id,tag_id) VALUES(?,?)",55L,7L);
    }
}
