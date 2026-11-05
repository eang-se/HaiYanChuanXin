package cn.haiyan.platform.web;
import cn.haiyan.platform.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class SearchControllerTest {
 @Test void allCategoriesReturnPublishedWorksAndEscapeWildcards(){
  CommunityStore db=mock(CommunityStore.class);CommunityService service=mock(CommunityService.class);when(service.store()).thenReturn(db);
  when(db.count(anyString(),any(Object[].class))).thenReturn(1L);when(service.decorate(anyList(),eq(false))).thenAnswer(i->i.getArgument(0));
  var search=new SearchController(service);
  for(String type:List.of("works","authors","tags"))assertEquals(1L,search.search("_%!",type,1).get("total"));
  verify(db).count(contains("w.plain_text LIKE"),eq("%!_!%!!%"),eq("%!_!%!!%"));
  verify(db).count(contains("member_pen_names"),eq("%!_!%!!%"),eq("%!_!%!!%"),eq("%!_!%!!%"),eq("%!_!%!!%"));
  verify(db).count(contains("work_tags"),eq("%!_!%!!%"),eq("%!_!%!!%"));
 }
 @Test void emptyAndInvalidSearchesAreBounded(){
  var controller=new SearchController(mock(CommunityService.class));assertEquals(0,controller.search(" ","works",1).get("total"));
  assertThrows(ResponseStatusException.class,()->controller.search("x","unknown",1));assertThrows(ResponseStatusException.class,()->controller.search("字".repeat(101),"works",1));
 }
}
