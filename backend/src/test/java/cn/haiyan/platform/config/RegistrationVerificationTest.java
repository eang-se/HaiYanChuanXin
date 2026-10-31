package cn.haiyan.platform.config;
import cn.haiyan.platform.service.*;
import cn.haiyan.platform.web.AuthController;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RegistrationVerificationTest {
 private final CommunityStore db=mock(CommunityStore.class);
 private final RegistrationPolicy policy=mock(RegistrationPolicy.class);
 private final QqEligibility eligibility=mock(QqEligibility.class);
 private final VerificationDelivery delivery=mock(VerificationDelivery.class);
 private final RegistrationVerification verification=new RegistrationVerification(db,policy,eligibility,delivery);
 @Test void rejectsRemovedPrivateMessageChannel(){
  when(eligibility.accepts("23456789")).thenReturn(true);
  assertThrows(ResponseStatusException.class,()->verification.send("23456789","qq"));verifyNoInteractions(delivery);
 }
 @Test void testModeAcceptsOnlyEligibleQqAndTestCode(){
  when(policy.testMode()).thenReturn(true);when(policy.isTestQq("12345")).thenReturn(true);when(eligibility.accepts("12345")).thenReturn(true);
  assertEquals("12345",verification.verify("12345","0000"));
  assertThrows(ResponseStatusException.class,()->verification.verify("12345","1234"));
  assertThrows(ResponseStatusException.class,()->verification.verify("54321","0000"));
  verifyNoInteractions(delivery);
 }
 @Test void rejectsAlreadyBoundRealQq(){
  when(policy.isTestQq("12345")).thenReturn(true);when(eligibility.accepts("12312345")).thenReturn(true);
  when(db.count("SELECT COUNT(*) FROM qq_invitation_bindings WHERE qq_number=?","12312345")).thenReturn(1L);
  assertThrows(ResponseStatusException.class,()->verification.verify("12312345","0000"));
  assertThrows(ResponseStatusException.class,()->verification.send("12312345","email"));
  verifyNoInteractions(delivery);
 }
 @Test void testQqCanBeReusedEvenWhenAlreadyBound(){
  when(policy.isTestQq("12345")).thenReturn(true);when(eligibility.accepts("12345")).thenReturn(true);
  when(db.count("SELECT COUNT(*) FROM qq_invitation_bindings WHERE qq_number=?","12345")).thenReturn(1L);
  assertEquals("12345",verification.verify("12345","0000"));
 }
 @Test void liveModeCannotUseTestBypass(){
  when(policy.testMode()).thenReturn(false);when(eligibility.accepts("12345")).thenReturn(true);
  assertThrows(ResponseStatusException.class,()->verification.verify("12345","0000"));
 }
 @Test void wrongCodeCountsAttemptAndValidCodeIsConsumed(){
  when(eligibility.accepts("12345")).thenReturn(true);
  when(db.one("SELECT code_hash FROM registration_codes WHERE qq_number=? AND expires_at>CURRENT_TIMESTAMP AND consumed=FALSE AND attempts<5 FOR UPDATE","12345")).thenReturn(new HashMap<>(Map.of("codeHash",AuthController.hash("654321"))));
  assertThrows(ResponseStatusException.class,()->verification.verify("12345","0000"));
  verify(db).update("UPDATE registration_codes SET attempts=attempts+1 WHERE qq_number=?","12345");
  assertEquals("12345",verification.verify("12345","654321"));
  verify(db).update("UPDATE registration_codes SET consumed=TRUE WHERE qq_number=?","12345");
 }
 @Test void unconfiguredDeliveryFailsClosed(){assertThrows(ResponseStatusException.class,()->new VerificationDeliveryConfig().verificationDelivery("","","missing-file-for-test.properties").send("12345","654321","email"));}
 @Test void realQqRequiresItsOwnCodeEvenWhenTestAccountIsEnabled(){
  when(policy.testMode()).thenReturn(true);when(eligibility.accepts("23456789")).thenReturn(true);
  assertThrows(ResponseStatusException.class,()->verification.verify("23456789","0000"));
  verifyNoInteractions(delivery);
 }
 @Test void invitedQqReceivesRandomCodeWhileTestAccountRemainsEnabled(){
  when(policy.testMode()).thenReturn(true);when(eligibility.accepts("23456789")).thenReturn(true);
  var result=verification.send("23456789","email");
  var code=org.mockito.ArgumentCaptor.forClass(String.class);verify(delivery).send(eq("23456789"),code.capture(),eq("email"));
  assertTrue(code.getValue().matches("[0-9]{6}"));assertEquals(false,result.get("testMode"));
  verify(db).update("UPDATE registration_codes SET code_hash=?,expires_at=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 10 MINUTE),sent_at=CURRENT_TIMESTAMP,attempts=0,consumed=FALSE WHERE qq_number=?",AuthController.hash(code.getValue()),"23456789");
 }
 @Test void failedDeliveryStillRecordsARequestForRateLimiting(){
  when(eligibility.accepts("23456789")).thenReturn(true);
  doThrow(cn.haiyan.platform.service.CommunityService.error(503,"发送失败")).when(delivery).send(eq("23456789"),anyString(),eq("email"));
  assertThrows(ResponseStatusException.class,()->verification.send("23456789","email"));
  verify(db).update("UPDATE registration_codes SET sent_at=CURRENT_TIMESTAMP WHERE qq_number=?","23456789");
 }
}
