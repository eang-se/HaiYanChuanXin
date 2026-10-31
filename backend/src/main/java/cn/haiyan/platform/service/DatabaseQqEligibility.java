package cn.haiyan.platform.service;
import org.springframework.stereotype.Service;
@Service
public class DatabaseQqEligibility implements QqEligibility {
 private final CommunityStore db;private final RegistrationPolicy policy;private final MarkdownQqDirectory directory;
 public DatabaseQqEligibility(CommunityStore db,RegistrationPolicy policy,MarkdownQqDirectory directory){this.db=db;this.policy=policy;this.directory=directory;}
 public boolean accepts(String qq){return policy.isTestQq(qq)||displayName(qq)!=null;}
 public String displayName(String qq){
  if(db.count("SELECT COUNT(*) FROM qq_allowlist WHERE qq_number=? AND active=FALSE",qq)>0)return null;
  return directory.name(qq);
 }
}
