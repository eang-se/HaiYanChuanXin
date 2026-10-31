package cn.haiyan.platform.web;
import cn.haiyan.platform.service.*;
import jakarta.servlet.http.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import static cn.haiyan.platform.service.CommunityService.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final CommunityService service; private final PasswordEncoder encoder;private final RegistrationPolicy policy;private final RegistrationVerification verification;private final PasswordResetService passwordReset;private final IdentityService identities;private final AccountPermissions permissions;private final RememberedDeviceService remembered;
    public AuthController(CommunityService service,PasswordEncoder encoder,RegistrationPolicy policy,RegistrationVerification verification,PasswordResetService passwordReset,IdentityService identities,AccountPermissions permissions,RememberedDeviceService remembered){this.service=service;this.encoder=encoder;this.policy=policy;this.verification=verification;this.passwordReset=passwordReset;this.identities=identities;this.permissions=permissions;this.remembered=remembered;}
    @GetMapping("/csrf") public Map<String,String> csrf(HttpServletRequest request) {
        CsrfToken t=(CsrfToken)request.getAttribute(CsrfToken.class.getName());return Map.of("token",t.getToken(),"headerName",t.getHeaderName());
    }
    @GetMapping("/me") public Map<String,Object> me(Principal p,HttpServletRequest request){return p==null?Map.of():view(p.getName(),request.getSession(false));}
    private Map<String,Object> view(String username,HttpSession session){
        var current=service.member(username);var actor=service.member(identities.actor(session,username));
        current.put("accountType",Objects.requireNonNullElse(current.get("accountType"),"PERSONAL"));
        current.put("actorId",actor.get("id"));current.put("actorRole",actor.get("role"));current.put("permissions",permissions.currentEffective(username));
        return current;
    }
    @GetMapping("/identities") public Map<String,Object> listIdentities(Principal principal,HttpServletRequest request){
        var actor=service.member(identities.actor(request.getSession(false),principal.getName()));long id=((Number)actor.get("id")).longValue();
        var options=new ArrayList<Map<String,Object>>();
        for(var row:identities.available(id)){
            var member=service.member((String)row.get("username"));
            options.add(Map.of("id",member.get("id"),"penName",member.get("penName"),"avatarUrl",member.get("avatarUrl"),"accountType",Objects.requireNonNullElse(member.get("accountType"),"PERSONAL"),"active",member.get("username").equals(principal.getName())));
        }
        return Map.of("actorId",id,"identities",options);
    }
    public record IdentitySelection(long id){}
    @PostMapping("/identity") public Map<String,Object> switchIdentity(Principal principal,@RequestBody IdentitySelection form,HttpServletRequest request,HttpServletResponse response){
        var session=request.getSession(false);var actor=service.member(identities.actor(session,principal.getName()));
        String selected=identities.select(session,((Number)actor.get("id")).longValue(),form.id());
        var previous=SecurityContextHolder.getContext().getAuthentication();var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(selected,null,previous.getAuthorities()));SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context,request,response);
        return view(selected,session);
    }
    public record Login(String username,String password,Boolean remember){public Login(String username,String password){this(username,password,Boolean.FALSE);}}
    @PostMapping("/login") public Map<String,Object> login(@RequestBody Login form,HttpServletRequest request,HttpServletResponse response) {
        var m=service.store().one("SELECT * FROM members WHERE username=?",form.username());
        if(m==null || !"PERSONAL".equals(m.get("accountType")) || form.password()==null || !encoder.matches(form.password(),(String)m.get("passwordHash")))throw error(401,"账号或密码不正确");
        if(Boolean.TRUE.equals(form.remember()))remembered.remember(request,response,m);else remembered.forget(request,response);
        return establish(m,request,response);
    }
    @GetMapping("/remembered") public Map<String,Object> remembered(HttpServletRequest request){return remembered.describe(request);}
    @PostMapping("/remembered/forget") public void forget(HttpServletRequest request,HttpServletResponse response){remembered.forget(request,response);}
    @PostMapping("/quick-login") public Map<String,Object> quickLogin(HttpServletRequest request,HttpServletResponse response){return establish(remembered.use(request,response),request,response);}
    private Map<String,Object> establish(Map<String,Object> m,HttpServletRequest request,HttpServletResponse response){
        String username=(String)m.get("username");
        var previous=request.getSession(false);if(previous!=null){identities.release(previous);previous.removeAttribute(IdentityService.SELECTED);}request.getSession(true);request.changeSessionId();request.getSession().setAttribute("credentialHash",m.get("passwordHash"));request.getSession().setAttribute(IdentityService.ACTOR,username);
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(username,null,List.of(new SimpleGrantedAuthority("ROLE_"+m.get("role")))));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context,request,response);
        return view(username,request.getSession(false));
    }
    @PostMapping("/logout") public void logout(HttpServletRequest request){var session=request.getSession(false);if(session!=null){identities.release(session);session.invalidate();}SecurityContextHolder.clearContext();}
    @GetMapping("/registration-options") public Map<String,Object> options(){return Map.of("enabled",policy.enabled(),"testMode",policy.testMode(),"channels",List.of("email"));}
    public record CodeRequest(String qq,String channel){}
    @PostMapping("/verification-code") public Map<String,Object> sendCode(@RequestBody CodeRequest form){return verification.send(form.qq(),form.channel());}
    public record ResetRequest(String username,String channel){}
    public record ResetPassword(String username,String code,String password){}
    @PostMapping("/password-reset/code") public Map<String,Object> sendReset(@RequestBody ResetRequest form){return passwordReset.send(form.username(),form.channel());}
    @PostMapping("/password-reset") public Map<String,String> reset(@RequestBody ResetPassword form){passwordReset.reset(form.username(),form.code(),form.password());return Map.of("message","密码已重设，请重新登录");}
    public record Registration(String username,String password,String qq,String code){}
    @PostMapping("/register") @Transactional(noRollbackFor=org.springframework.web.server.ResponseStatusException.class) public Map<String,String> register(@RequestBody Registration form){
        if(form.username()==null || !form.username().matches("[a-zA-Z0-9_]{3,30}"))throw error(400,"账号需为 3～30 位字母、数字或下划线");
        if(form.password()==null || form.password().length()<10 || form.password().getBytes(StandardCharsets.UTF_8).length>72)throw error(400,"密码至少 10 位，且不超过 72 字节");
        if(service.store().count("SELECT COUNT(*) FROM members WHERE username=?",form.username())>0)throw error(400,"账号已存在");
        String qq=verification.verify(form.qq(),form.code());
        String defaultName=verification.defaultName(qq,form.username());
        try {
            long memberId=service.store().insert("INSERT INTO members(username,password_hash,pen_name,qq_number,profile_completed,profile_prompt_seen) VALUES(?,?,?,?,FALSE,FALSE)",form.username(),encoder.encode(form.password()),defaultName,qq);
            service.store().update(policy.isTestQq(qq)?"INSERT IGNORE INTO qq_invitation_bindings(qq_number,member_id) VALUES(?,?)":"INSERT INTO qq_invitation_bindings(qq_number,member_id) VALUES(?,?)",qq,memberId);
        } catch(org.springframework.dao.DuplicateKeyException ex) {
            org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw error(400,"此 QQ 号已绑定账号或账号已存在");
        }
        return Map.of("message","注册成功，请登录");
    }
    public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
