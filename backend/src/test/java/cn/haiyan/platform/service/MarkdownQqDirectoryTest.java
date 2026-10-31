package cn.haiyan.platform.service;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarkdownQqDirectoryTest {
 @TempDir Path root;
 @Test void readsNamesAndPicksUpFileChangesImmediately() throws Exception {
  var file=root.resolve("名单.md");Files.writeString(file,"123456 测试昵称\n234567 另一个 昵称\n",StandardCharsets.UTF_8);
  var roster=new MarkdownQqDirectory(root.toString());
  assertEquals("测试昵称",roster.name("123456"));assertEquals("另一个 昵称",roster.name("234567"));
  Files.writeString(file,"345678 新名称\n",StandardCharsets.UTF_8);
  assertNull(roster.name("123456"));assertEquals("新名称",roster.name("345678"));
 }
 @Test void rejectsConflictingOrMalformedEntries() throws Exception {
  Files.writeString(root.resolve("a.md"),"123456 甲\n",StandardCharsets.UTF_8);
  Files.writeString(root.resolve("b.md"),"123456 乙\n",StandardCharsets.UTF_8);
  var roster=new MarkdownQqDirectory(root.toString());assertEquals("甲",roster.groups().get("a").get("123456"));assertEquals("乙",roster.groups().get("b").get("123456"));assertEquals("甲",roster.name("123456"));
  Files.writeString(root.resolve("a.md"),"123456 甲\n123456 乙\n",StandardCharsets.UTF_8);assertThrows(ResponseStatusException.class,roster::entries);
  Files.writeString(root.resolve("a.md"),"123456 甲\n",StandardCharsets.UTF_8);
  Files.writeString(root.resolve("b.md"),"this is not a roster entry\n",StandardCharsets.UTF_8);
  assertThrows(ResponseStatusException.class,roster::entries);
 }
 @Test void eligibilityUsesRosterAndAdminDisabledOverride(){
  var db=mock(CommunityStore.class);var policy=mock(RegistrationPolicy.class);var roster=mock(MarkdownQqDirectory.class);
  when(roster.name("234567")).thenReturn("名称");
  var eligibility=new DatabaseQqEligibility(db,policy,roster);
  assertTrue(eligibility.accepts("234567"));assertFalse(eligibility.accepts("345678"));
  when(db.count("SELECT COUNT(*) FROM qq_allowlist WHERE qq_number=? AND active=FALSE","234567")).thenReturn(1L);
  assertFalse(eligibility.accepts("234567"));
  when(policy.isTestQq("12345")).thenReturn(true);assertTrue(eligibility.accepts("12345"));
 }
 @Test void safelyLimitsLongUnicodeName() throws Exception {
  String name="🌸".repeat(45);Files.writeString(root.resolve("a.md"),"123456 "+name+"\n",StandardCharsets.UTF_8);
  String actual=new MarkdownQqDirectory(root.toString()).name("123456");assertEquals(40,actual.codePointCount(0,actual.length()));
 }
}
