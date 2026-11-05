package cn.haiyan.platform.web;

import cn.haiyan.platform.service.*;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CommentModerationTest {
 private final CommunityService service=mock(CommunityService.class);
 private final AccountPermissions permissions=mock(AccountPermissions.class);
 private final CommunityStore store=mock(CommunityStore.class);
 private final CommunityController controller=new CommunityController(service,permissions,mock(ArticlePublicationService.class));
 private final Principal user=()->"author";
 @Test void sensitiveCommentsAreRejectedBeforeInsertion(){
  for(String text:new String[]{"博彩","广告代理","你这个傻逼"}){
   var ex=assertThrows(ResponseStatusException.class,()->controller.comment(7,user,new CommunityController.TextBody(text)));
   assertEquals(422,ex.getStatusCode().value());assertTrue(ex.getReason().contains("敏感内容"));
  }
  verify(service,never()).store();
 }
 @Test void ordinaryCommentsAreStored(){
  when(service.store()).thenReturn(store);when(service.memberId("author")).thenReturn(12L);
  controller.comment(7,user,new CommunityController.TextBody("写得很好，谢谢分享。"));
  verify(store).insert("INSERT INTO comments(member_id,work_id,body) VALUES(?,?,?)",12L,7L,"写得很好，谢谢分享。");
 }
}
