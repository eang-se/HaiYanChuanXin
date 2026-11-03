package cn.haiyan.platform.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkTagsTest {
    private final CommunityStore db=mock(CommunityStore.class);
    private final WorkTags tags=new WorkTags(db);
    private final String lookup="SELECT kind,name,is_activity,activity_active FROM tags WHERE id=? FOR UPDATE";
    private Map<String,Object> activity(boolean active){return Map.of("kind","TOPIC","name","秋日来信","isActivity",true,"activityActive",active);}
    private void content(String name,long id){when(db.one("SELECT id FROM tags WHERE name=? AND kind='TOPIC' AND is_activity=FALSE",name)).thenReturn(Map.of("id",id));}

    @Test void sameNamedContentAndActivityHaveIndependentRelations(){
        when(db.one(lookup,1L)).thenReturn(activity(true));content("秋日来信",9L);
        assertEquals(List.of(9L),tags.resolve(null,null,List.of(),List.of("秋日来信")));
        assertEquals(List.of(1L,9L),tags.resolve(null,null,List.of(1L),List.of("秋日来信")));
        verify(db,times(2)).update(startsWith("INSERT INTO tags(name,description,is_activity,kind)"),eq("秋日来信"));
    }
    @Test void contentNamesNormalizeAndDeduplicateAndDoNotRequireExistingTags(){
        content("校园",9L);content("Reading",10L);
        assertEquals(List.of(9L,10L),tags.resolve(null,null,List.of(),List.of(" #校园 ","校园","Reading","reading")));
    }
    @Test void forgedActivitySelectionCannotUseContentOrCollectionIds(){
        when(db.one(lookup,9L)).thenReturn(Map.of("kind","TOPIC","name","校园","isActivity",false));
        when(db.one(lookup,10L)).thenReturn(Map.of("kind","COLLECTION","name","合集：校园"));
        for(Long id:Arrays.asList(9L,10L,999L,null))assertThrows(ResponseStatusException.class,()->tags.resolve(null,null,Collections.singletonList(id),List.of()));
    }
    @Test void closedActivityRejectsNewParticipationAndRetainsHistoricalParticipation(){
        when(db.one(lookup,1L)).thenReturn(activity(false));
        assertEquals(400,assertThrows(ResponseStatusException.class,()->tags.resolve(null,null,List.of(1L),List.of())).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->tags.resolve(55L,null,List.of(1L),List.of()));
        when(db.count("SELECT COUNT(*) FROM work_tags WHERE work_id=? AND tag_id=?",55L,1L)).thenReturn(1L);
        assertEquals(List.of(1L),tags.resolve(55L,null,List.of(1L),List.of()));
        assertEquals(List.of(),tags.resolve(55L,null,List.of(),List.of()));
    }
    @Test void limitsAndInvalidContentAreRejectedBeforeInsertion(){
        for(List<String> names:List.of(List.of("海燕精选"),List.of("字".repeat(31)),List.of(" "),List.of("校\u0000园"),List.of("a","b","c","d","e","f")))
            assertThrows(ResponseStatusException.class,()->tags.resolve(null,null,List.of(),names));
        assertThrows(ResponseStatusException.class,()->tags.resolve(null,null,List.of(1L,2L,3L,4L,5L,6L),List.of()));
        verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void activitySavedBeforeClosingCanBeRetainedInAPrivateRevision(){
        when(db.one(lookup,1L)).thenReturn(activity(false));
        when(db.one("SELECT payload FROM work_drafts WHERE work_id=?",55L)).thenReturn(Map.of("payload","{\"tagIds\":[1]}"));
        assertEquals(List.of(1L),tags.resolve(55L,null,List.of(1L),List.of()));
        when(db.one("SELECT payload FROM work_drafts WHERE work_id=?",55L)).thenReturn(Map.of("payload","{\"tagIds\":[9]}"));
        assertThrows(ResponseStatusException.class,()->tags.resolve(55L,null,List.of(1L),List.of()));
    }
    @Test void legacyTagsRemainCompatibleAndActivityAvailabilityCannotBeBypassed(){
        when(db.one(lookup,1L)).thenReturn(activity(true));
        when(db.one(lookup,9L)).thenReturn(Map.of("kind","TOPIC","name","校园","isActivity",false));
        assertEquals(List.of(1L,9L),tags.resolve(null,List.of(1L,9L),null,null));
        when(db.one(lookup,1L)).thenReturn(activity(false));
        assertThrows(ResponseStatusException.class,()->tags.resolve(null,List.of(1L),null,null));
        assertThrows(ResponseStatusException.class,()->tags.resolve(null,List.of(9L),List.of(),List.of()));
    }
}
