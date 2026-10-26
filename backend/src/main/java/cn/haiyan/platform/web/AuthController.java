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
    private final CommunityService service; private final PasswordEncoder encoder;private final RegistrationPolicy policy;private final RegistrationVerification verification;
    public AuthController(CommunityService service,PasswordEncoder encoder,RegistrationPolicy policy,RegistrationVerification verification){this.service=service;this.encoder=encoder;this.policy=policy;this.verification=verification;}
    @GetMapping("/csrf") public Map<String,String> csrf(HttpServletRequest request) {
        CsrfToken t=(CsrfToken)request.getAttribute(CsrfToken.class.getName());return Map.of("token",t.getToken(),"headerName",t.getHeaderName());
    }
    @GetMapping("/me") public Map<String,Object> me(Principal p){return p==null?Map.of():service.member(p.getName());}
    public record Login(String username,String password){}
    @PostMapping("/login") public Map<String,Object> login(@RequestBody Login form,HttpServletRequest request,HttpServletResponse response) {
        var m=service.store().one("SELECT * FROM members WHERE username=?",form.username());
        if(m==null || form.password()==null || !encoder.matches(form.password(),(String)m.get("passwordHash")))throw error(401,"账号或密码不正确");
        request.getSession(true);request.changeSessionId();
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(form.username(),null,List.of(new SimpleGrantedAuthority("ROLE_"+m.get("role")))));
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context,request,response);
        return service.member(form.username());
    }
    @PostMapping("/logout") public void logout(HttpServletRequest request){var session=request.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();}
    @GetMapping("/registration-options") public Map<String,Object> options(){return Map.of("enabled",policy.enabled(),"testMode",policy.testMode(),"channels",List.of("email","bot"));}
    public record CodeRequest(String qq,String channel){}
    @PostMapping("/verification-code") public Map<String,Object> sendCode(@RequestBody CodeRequest form){return verification.send(form.qq(),form.channel());}
    public record Registration(String username,String password,String qq,String code){}
    @PostMapping("/register") public Map<String,String> register(@RequestBody Registration form){
        if(form.username()==null || !form.username().matches("[a-zA-Z0-9_]{3,30}"))throw error(400,"账号需为 3～30 位字母、数字或下划线");
        if(form.password()==null || form.password().length()<10 || form.password().getBytes(StandardCharsets.UTF_8).length>72)throw error(400,"密码至少 10 位，且不超过 72 字节");
        if(service.store().count("SELECT COUNT(*) FROM members WHERE username=?",form.username())>0)throw error(400,"账号已存在");
        String qq=verification.verify(form.qq(),form.code());
        service.store().insert("INSERT INTO members(username,password_hash,pen_name,qq_number,profile_completed,profile_prompt_seen) VALUES(?,?,?,?,FALSE,FALSE)",form.username(),encoder.encode(form.password()),form.username(),qq);
        return Map.of("message","注册成功，请登录");
    }
    public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
