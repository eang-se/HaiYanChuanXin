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
 @Test void testModeAcceptsOnlyEligibleQqAndTestCode(){
  when(policy.testMode()).thenReturn(true);when(eligibility.accepts("12345")).thenReturn(true);
  assertEquals("12345",verification.verify("12345","0000"));
  assertThrows(ResponseStatusException.class,()->verification.verify("12345","1234"));
  assertThrows(ResponseStatusException.class,()->verification.verify("54321","0000"));
  verifyNoInteractions(delivery);
 }
 @Test void liveModeCannotUseTestBypass(){
  when(policy.testMode()).thenReturn(false);when(eligibility.accepts("12345")).thenReturn(true);
  assertThrows(ResponseStatusException.class,()->verification.verify("12345","0000"));
 }
 @Test void wrongCodeCountsAttemptAndValidCodeIsConsumed(){
  when(eligibility.accepts("12345")).thenReturn(true);
  when(db.one("SELECT * FROM registration_codes WHERE qq_number=? FOR UPDATE","12345")).thenReturn(new HashMap<>(Map.of("codeHash",AuthController.hash("654321"))));
  when(db.count("SELECT COUNT(*) FROM registration_codes WHERE qq_number=? AND expires_at>CURRENT_TIMESTAMP AND consumed=FALSE AND attempts<5","12345")).thenReturn(1L);
  assertThrows(ResponseStatusException.class,()->verification.verify("12345","0000"));
  verify(db).update("UPDATE registration_codes SET attempts=attempts+1 WHERE qq_number=?","12345");
  assertEquals("12345",verification.verify("12345","654321"));
  verify(db).update("UPDATE registration_codes SET consumed=TRUE WHERE qq_number=?","12345");
 }
 @Test void defaultDeliveryFailsClosed(){assertThrows(ResponseStatusException.class,()->new VerificationDeliveryConfig().verificationDelivery().send("12345","654321","email"));}
}
