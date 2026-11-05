package cn.haiyan.platform.web;
import cn.haiyan.platform.service.*;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CollectionTitleLimitTest {
 @Test void collectionTitleLimitCountsUnicodeCharacters(){
  var service=mock(CommunityService.class);var store=mock(CommunityStore.class);var permissions=mock(AccountPermissions.class);
  when(service.store()).thenReturn(store);when(service.memberId("writer")).thenReturn(8L);Principal principal=()->"writer";
  var controller=new CollectionController(service,permissions);
  assertThrows(ResponseStatusException.class,()->controller.create(principal,new CollectionController.Input("字".repeat(26),"","连载",false,List.of())));
  assertThrows(ResponseStatusException.class,()->controller.create(principal,new CollectionController.Input("😀".repeat(26),"","连载",false,List.of())));
  assertDoesNotThrow(()->controller.create(principal,new CollectionController.Input("😀".repeat(25),"","连载",false,List.of())));
 }
}
