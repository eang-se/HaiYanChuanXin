package cn.haiyan.platform.service;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import cn.haiyan.platform.web.AuthController;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {
 private final CommunityStore db=mock(CommunityStore.class);
 private final RegistrationPolicy policy=mock(RegistrationPolicy.class);
 private final VerificationDelivery delivery=mock(VerificationDelivery.class);
 private final PasswordEncoder encoder=mock(PasswordEncoder.class);
 private final PasswordResetService service=new PasswordResetService(db,policy,delivery,encoder);
 @Test void rejectsRemovedPrivateMessageChannel(){
  assertThrows(ResponseStatusException.class,()->service.send("alice","qq"));verifyNoInteractions(delivery);
 }
 @Test void usesBoundQqForEmailAndGeneratesCode(){
  when(db.one("SELECT id,qq_number FROM members WHERE username=? AND account_type='PERSONAL'","alice")).thenReturn(Map.of("id",1L,"qqNumber","23456789"));
  service.send("alice");
  verify(delivery).sendPasswordReset(eq("23456789"),matches("[0-9]{6}"));
 }
 @Test void testQqRequiresRequestedCodeAndDoesNotSendEmail(){
  when(db.one("SELECT id,qq_number FROM members WHERE username=? AND account_type='PERSONAL'","alice")).thenReturn(Map.of("id",1L,"qqNumber","12345"));
  when(policy.isTestQq("12345")).thenReturn(true);
  service.send("alice");verifyNoInteractions(delivery);
  verify(db).update(startsWith("UPDATE password_reset_codes SET qq_number"),eq("12345"),eq(AuthController.hash("0000")),eq(1L));
 }
 @Test void rejectsWrongCodeAndConsumesCorrectCode(){
  when(db.one("SELECT id,qq_number FROM members WHERE username=? AND account_type='PERSONAL'","alice")).thenReturn(Map.of("id",1L,"qqNumber","23456789"));
  when(db.one("SELECT code_hash FROM password_reset_codes WHERE member_id=? AND qq_number=? AND expires_at>CURRENT_TIMESTAMP AND consumed=FALSE AND attempts<5 FOR UPDATE",1L,"23456789")).thenReturn(Map.of("codeHash",AuthController.hash("123456")));
  assertThrows(ResponseStatusException.class,()->service.reset("alice","000000","newpassword123"));
  verify(db).update("UPDATE password_reset_codes SET attempts=attempts+1 WHERE member_id=?",1L);
  when(encoder.encode("newpassword123")).thenReturn("encoded");
  service.reset("alice","123456","newpassword123");
  verify(db).update("UPDATE members SET password_hash=? WHERE id=?","encoded",1L);
  verify(db).update("UPDATE password_reset_codes SET consumed=TRUE WHERE member_id=?",1L);
 }
 @Test void missingBindingDoesNotSendMail(){
  when(db.one("SELECT id,qq_number FROM members WHERE username=? AND account_type='PERSONAL'","oldaccount")).thenReturn(Map.of("id",1L,"qqNumber",""));
  service.send("oldaccount");verifyNoInteractions(delivery);
  assertThrows(ResponseStatusException.class,()->service.reset("oldaccount","0000","newpassword123"));
 }
}
