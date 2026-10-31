package cn.haiyan.platform.config;
import cn.haiyan.platform.service.QqEmailDelivery;
import cn.haiyan.platform.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QqEmailDeliveryTest {
 @Test void sendsOnlyToMailboxMatchingInvitedNumber(){
  var sender=mock(JavaMailSender.class);var delivery=new QqEmailDelivery(sender,"sender@foxmail.com");
  delivery.send("23456789","654321","email");
  var captured=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender).send(captured.capture());
  assertArrayEquals(new String[]{"23456789@qq.com"},captured.getValue().getTo());
  assertEquals("sender@foxmail.com",captured.getValue().getFrom());
  assertTrue(captured.getValue().getText().contains("654321"));
 }
 @Test void passwordResetEmailNamesItsPurpose(){
  var sender=mock(JavaMailSender.class);var delivery=new QqEmailDelivery(sender,"sender@foxmail.com");
  delivery.sendPasswordReset("23456789","654321");
  var captured=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender).send(captured.capture());
  assertArrayEquals(new String[]{"23456789@qq.com"},captured.getValue().getTo());
  assertTrue(captured.getValue().getSubject().contains("密码"));
 }
 @Test void rejectsMalformedAddressAndReportsDeliveryFailureWithoutDetails(){
  var sender=mock(JavaMailSender.class);var delivery=new QqEmailDelivery(sender,"sender@foxmail.com");
  assertThrows(ResponseStatusException.class,()->delivery.send("12345@example.com","123456","email"));
  doThrow(new MailSendException("private SMTP details")).when(sender).send(any(SimpleMailMessage.class));
  var error=assertThrows(ResponseStatusException.class,()->delivery.send("23456789","123456","email"));
  assertEquals(503,error.getStatusCode().value());assertFalse(error.getReason().contains("private"));
 }
 @Test void testModeStillAcceptsRealEntriesFromList(){
  var store=mock(CommunityStore.class);var policy=mock(RegistrationPolicy.class);
  when(policy.isTestQq("12345")).thenReturn(true);
  var directory=mock(MarkdownQqDirectory.class);when(directory.name("23456789")).thenReturn("社员名称");
  var eligibility=new DatabaseQqEligibility(store,policy,directory);
  assertTrue(eligibility.accepts("12345"));assertTrue(eligibility.accepts("23456789"));assertFalse(eligibility.accepts("34567890"));
 }
 @Test void realmConfigurationCanSupplyTheQqMailSender(@TempDir Path folder)throws Exception{
  var config=folder.resolve("realm-local.properties");Files.writeString(config,"email.smtp.email=sender@foxmail.com\nemail.smtp.code=example-auth-code\n");
  assertInstanceOf(QqEmailDelivery.class,new VerificationDeliveryConfig().verificationDelivery("","",config.toString()));
 }
}
