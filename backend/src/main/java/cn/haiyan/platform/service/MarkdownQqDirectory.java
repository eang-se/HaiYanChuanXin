package cn.haiyan.platform.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
}
