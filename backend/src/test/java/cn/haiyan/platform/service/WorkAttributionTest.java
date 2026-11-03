package cn.haiyan.platform.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkAttributionTest {
    private final PenNames pens=mock(PenNames.class);
    @Test void anonymousClearsPenBindingWithoutChangingThePublishingAccount(){
        var credit=WorkAttribution.resolve(pens,29L,"ANONYMOUS","伪造署名",999L,null);
        assertEquals("佚名",credit.byline());assertNull(credit.penNameId());assertEquals("ANONYMOUS",credit.mode());verifyNoInteractions(pens);
        var db=mock(CommunityStore.class);var service=new CommunityService(db,mock(WorkCollections.class),mock(AccountPermissions.class),new PenNames(db));
        var work=new HashMap<String,Object>(Map.of("id",55L,"authorId",29L,"authorName","发布账号","bylineMode","ANONYMOUS","byline","佚名","displayByline","佚名","genre","散文","plainText","正文"));
        service.decorate(List.of(work),false);
        assertEquals(29L,work.get("authorId"));assertEquals("发布账号",work.get("authorName"));assertEquals("佚名",work.get("displayByline"));
    }
    @Test void temporaryCreditIsNeverBoundBySavingOrPublishingAnArticle(){
        var credit=WorkAttribution.resolve(pens,29L,"TEMPORARY","  临时来客  ",999L,null);
        assertEquals("临时来客",credit.byline());assertNull(credit.penNameId());verifyNoInteractions(pens);
    }
    @Test void boundNamesRequireAnExistingAuthorizedBinding(){
        when(pens.resolve(29L,3L,null)).thenReturn(Map.of("id",3L,"name","林间"));
        var credit=WorkAttribution.resolve(pens,29L,"BOUND","其他名字",3L,null);
        assertEquals(3L,credit.penNameId());assertEquals("林间",credit.byline());
        when(pens.resolve(29L,99L,null)).thenReturn(null);
        assertThrows(ResponseStatusException.class,()->WorkAttribution.resolve(pens,29L,"BOUND","名字",99L,null));
    }
    @Test void emptyReservedAndInvalidModesDoNotProduceACredit(){
        for(String name:List.of(" ","佚名","署名待核","字".repeat(121)))assertThrows(ResponseStatusException.class,()->WorkAttribution.resolve(pens,29L,"TEMPORARY",name,0L,null));
        assertThrows(ResponseStatusException.class,()->WorkAttribution.resolve(pens,29L,"OTHER","名字",0L,null));
    }
}
