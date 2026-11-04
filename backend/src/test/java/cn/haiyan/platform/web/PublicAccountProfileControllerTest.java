package cn.haiyan.platform.web;

import cn.haiyan.platform.service.CommunityStore;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicAccountProfileControllerTest {
 @TempDir Path root;
 @Test void avatarUploadIsIndependentOfProfileAndServesOnlyAValidPublicAccount() throws Exception {
  var db=mock(CommunityStore.class);var controller=new PublicAccountProfileController(db,root);
  when(db.count("SELECT COUNT(*) FROM members WHERE id=? AND account_type='PUBLIC' AND deleted_at IS NULL",7L)).thenReturn(1L);
  when(db.update(startsWith("UPDATE members SET avatar_url=?"),any(),eq(7L))).thenReturn(1);
  var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(3,3,BufferedImage.TYPE_INT_RGB),"png",bytes);
  var result=controller.upload(7,new MockMultipartFile("file","avatar.png","image/png",bytes.toByteArray()));String url=result.get("avatarUrl");
  assertTrue(url.startsWith("/api/public/account-avatars/"));assertTrue(java.nio.file.Files.isRegularFile(root.resolve(url.substring(url.lastIndexOf('/')+1)+".jpg")));
  verify(db,never()).update(startsWith("UPDATE members SET pen_name"),any(),any(),any());
  when(db.count("SELECT COUNT(*) FROM members WHERE avatar_url=? AND account_type='PUBLIC' AND deleted_at IS NULL",url)).thenReturn(1L);
  assertEquals(200,controller.avatar(url.substring(url.lastIndexOf('/')+1)).getStatusCode().value());
  assertThrows(ResponseStatusException.class,()->controller.upload(7,new MockMultipartFile("file","bad.png","image/png",new byte[]{1,2,3})));
  assertThrows(ResponseStatusException.class,()->controller.upload(8,new MockMultipartFile("file","a.png","image/png",bytes.toByteArray())));
 }
 @Test void publicProfileSavesUnifiedIntroductionOnly(){
  var db=mock(CommunityStore.class);var controller=new PublicAccountProfileController(db,root);
  when(db.update(anyString(),any(),any(),eq(7L))).thenReturn(1);
  controller.update(7,new PublicAccountProfileController.Profile("编辑部","新的简介\n原有签名"));
  verify(db).update("UPDATE members SET pen_name=?,bio=?,signature='' WHERE id=? AND account_type='PUBLIC' AND deleted_at IS NULL","编辑部","新的简介\n原有签名",7L);
  assertThrows(ResponseStatusException.class,()->controller.update(7,new PublicAccountProfileController.Profile(" ","简介")));
 }
}
