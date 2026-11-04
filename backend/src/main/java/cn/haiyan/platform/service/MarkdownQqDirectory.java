package cn.haiyan.platform.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static cn.haiyan.platform.service.CommunityService.error;

/** One Markdown file is one group; files are re-read on every request. */
@Service
public class MarkdownQqDirectory {
 private static final Pattern ENTRY=Pattern.compile("^\\s*([1-9][0-9]{4,11})\\s+(.+?)\\s*$");
 private final Path directory;
 public MarkdownQqDirectory(@Value("${haiyan.qq-whitelist-dir:QQ_Whitelist}") String directory){this.directory=Path.of(directory).toAbsolutePath().normalize();}
 public Map<String,Map<String,String>> groups(){
  if(!Files.isDirectory(directory))throw error(503,"QQ 名单目录不可用，请联系管理员");
  Map<String,Map<String,String>> groups=new LinkedHashMap<>();
  try(var files=Files.list(directory)){
   for(Path file:files.filter(Files::isRegularFile).filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md")).sorted().toList()){
    String filename=file.getFileName().toString(),group=filename.substring(0,filename.length()-3);
    if(group.isBlank()||group.length()>100||groups.containsKey(group))throw error(503,"QQ 群名单文件名无效，请联系管理员");
    if(Files.size(file)>1024*1024)throw error(503,"QQ 名单文件过大，请联系管理员");
    Map<String,String> entries=new LinkedHashMap<>();
    for(String raw:Files.readAllLines(file,StandardCharsets.UTF_8)){
     String line=raw.replace("\uFEFF","").trim();
     if(line.isEmpty()||line.startsWith("#")||line.startsWith("<!--"))continue;
     var match=ENTRY.matcher(line);if(!match.matches())throw error(503,"QQ 名单格式错误，请联系管理员");
     String qq=match.group(1),name=match.group(2).replaceAll("\\p{Cntrl}","").trim();
     if(name.isBlank())throw error(503,"QQ 名单中存在空名称，请联系管理员");
     int count=name.codePointCount(0,name.length());if(count>40)name=name.substring(0,name.offsetByCodePoints(0,40));
     String previous=entries.putIfAbsent(qq,name);
     if(previous!=null&&!previous.equals(name))throw error(503,"同一群名单包含冲突的重复号码，请联系管理员");
    }
    groups.put(group,Collections.unmodifiableMap(entries));
   }
  } catch(IOException ex){throw error(503,"无法读取 QQ 名单，请联系管理员");}
  return Collections.unmodifiableMap(groups);
 }
 public Map<String,String> entries(){var result=new LinkedHashMap<String,String>();groups().values().forEach(group->group.forEach(result::putIfAbsent));return Collections.unmodifiableMap(result);}
 public String name(String qq){return entries().get(qq);}
 /** Validate a group file before adding it or explicitly replacing an existing roster. */
 public Map<String,Object> upload(String filename,byte[] bytes,boolean replace){
  if(filename==null||!filename.matches("(?i)[\\p{L}\\p{N}_ .（）()\\-]{1,100}\\.md")||filename.startsWith(".")||filename.contains(".."))throw error(400,"文件名须为群名或群号加 .md，不能包含路径");
  if(bytes==null||bytes.length==0||bytes.length>1024*1024)throw error(400,"名单文件须为 1 MB 以内的 UTF-8 Markdown 文件");
  String text;try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(java.nio.charset.CharacterCodingException ex){throw error(400,"请使用 UTF-8 编码的名单文件");}
  Map<String,String> entries=new LinkedHashMap<>();
  for(String raw:text.split("\\R")){
   String line=raw.replace("\uFEFF","").trim();if(line.isEmpty()||line.startsWith("#")||line.startsWith("<!--"))continue;
   var match=ENTRY.matcher(line);if(!match.matches())throw error(400,"名单格式错误：每行填写 QQ 号、空格和群内名称");
   String qq=match.group(1),name=match.group(2).replaceAll("\\p{Cntrl}","").trim();if(name.isBlank())throw error(400,"群内名称不能为空");
   int length=name.codePointCount(0,name.length());if(length>40)name=name.substring(0,name.offsetByCodePoints(0,40));
   String previous=entries.putIfAbsent(qq,name);if(previous!=null&&!previous.equals(name))throw error(400,"同一群名单包含冲突的重复号码");
  }
  if(entries.isEmpty())throw error(400,"名单中没有有效的 QQ 号");
  String group=filename.substring(0,filename.length()-3);Path target=directory.resolve(filename).normalize();
  if(!target.getParent().equals(directory))throw error(400,"文件名无效");
  Path temporary=null;
  try{
   if(!Files.isDirectory(directory))throw error(503,"QQ 名单目录不可用，请联系管理员");
   temporary=Files.createTempFile(directory,".roster-",".tmp");Files.write(temporary,bytes);
   if(replace)Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);
   else Files.move(temporary,target);
   temporary=null;
  }catch(FileAlreadyExistsException ex){throw error(409,"同名群名单已存在，请更换文件名");}
   catch(IOException ex){throw error(503,"无法保存名单文件，请检查服务器目录权限");}
   finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
  return Map.of("groupId",group,"count",entries.size());
 }
}
