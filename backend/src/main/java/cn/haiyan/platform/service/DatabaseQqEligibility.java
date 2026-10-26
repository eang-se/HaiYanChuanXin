package cn.haiyan.platform.service;
import org.springframework.stereotype.Service;
@Service
public class DatabaseQqEligibility implements QqEligibility {
 private final CommunityStore db;private final RegistrationPolicy policy;
 public DatabaseQqEligibility(CommunityStore db,RegistrationPolicy policy){this.db=db;this.policy=policy;}
 public boolean accepts(String qq){return policy.testMode()?"12345".equals(qq):db.count("SELECT COUNT(*) FROM qq_allowlist WHERE qq_number=? AND active=TRUE",qq)>0;}
}
