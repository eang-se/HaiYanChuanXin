package cn.haiyan.platform.web;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import cn.haiyan.platform.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PenNameControllerTest {
 @Test void returnsAssociatedAccountsWithoutPrivateIdentifiers(){
  var db=mock(CommunityStore.class);var service=mock(CommunityService.class);when(service.store()).thenReturn(db);
  when(db.one(contains("FROM pen_names p WHERE p.id=?"),eq(7L))).thenReturn(new HashMap<>(Map.of("id",7L,"name","海风","workCount",1L)));
  var account=new HashMap<String,Object>(Map.of("id",3L,"penName","作者","qqNumber","23456789","username","author","accountType","PERSONAL","bound",1));
  var historic=new HashMap<String,Object>(Map.of("id",4L,"penName","旧作者","username","previous","accountType","PERSONAL","bound",0));
  when(db.list(contains("member_pen_names"),eq(7L),eq(7L),eq(7L))).thenReturn(List.of(account,historic));
  var result=new PenNameController(service,mock(PenNames.class),mock(AccountPermissions.class)).detail(7L);
  var accounts=(List<?>)result.get("accounts");var item=(Map<?,?>)accounts.getFirst();
  assertEquals("/api/public/avatars/3",item.get("avatarUrl"));assertEquals(true,item.get("bound"));assertEquals(false,((Map<?,?>)accounts.get(1)).get("bound"));assertFalse(item.containsKey("qqNumber"));assertFalse(item.containsKey("username"));
  verify(db).list(contains("w.status='PUBLISHED'"),eq(7L),eq(7L),eq(7L));
 }
 @Test void unpublishedPenNameIsNotExposed(){
  var db=mock(CommunityStore.class);var service=mock(CommunityService.class);when(service.store()).thenReturn(db);
  when(db.one(contains("FROM pen_names p WHERE p.id=?"),eq(7L))).thenReturn(new HashMap<>(Map.of("id",7L,"name","海风","workCount",0L)));
  assertThrows(ResponseStatusException.class,()->new PenNameController(service,mock(PenNames.class),mock(AccountPermissions.class)).detail(7L));
  verify(db,never()).list(anyString(),any());
 }
}
