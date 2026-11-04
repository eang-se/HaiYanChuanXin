package cn.haiyan.platform.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkDecorationBatchTest {
    @Test void batchesRelationsAndPreservesPrivateCollectionVisibility(){
        CommunityStore db=mock(CommunityStore.class);
        var service=new CommunityService(db,mock(WorkCollections.class),mock(AccountPermissions.class),mock(PenNames.class));
        when(db.list(anyString(),any(Object[].class))).thenAnswer(call->{
            String sql=call.getArgument(0);
            if(sql.contains("decorated_work_id"))return new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("decoratedWorkId",1L,"id",7L,"name","主题"))));
            if(sql.contains("FROM collections"))return List.of(new LinkedHashMap<>(Map.of("id",9L,"title","私密合集","kind","SERIES","published",false)));
            return List.of();
        });
        var own=new LinkedHashMap<String,Object>(Map.of("id",1L,"authorId",5L,"collectionId",9L,"genre","散文","plainText","一 😀"));
        var other=new LinkedHashMap<String,Object>(Map.of("id",2L,"authorId",6L,"collectionId",9L,"genre","散文","plainText","正文"));
        service.decorate(new ArrayList<>(List.of(own,other)),false,5L);
        assertEquals(2,own.get("wordCount"));
        assertEquals(1,((List<?>)own.get("tags")).size());
        assertNotNull(own.get("collection"));
        assertNull(other.get("collection"));
        assertNull(other.get("collectionId"));
        verify(db,times(2)).list(anyString(),any(Object[].class));
    }
}
