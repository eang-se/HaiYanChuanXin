package cn.haiyan.platform.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import cn.haiyan.platform.service.CommunityStore;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,CommunityStore store,cn.haiyan.platform.service.IdentityService identities) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/brand/**", "/favicon.svg",
                                "/api/public/**", "/api/auth/me", "/api/auth/csrf", "/api/auth/registration-options", "/api/auth/remembered", "/api/media/**", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/quick-login", "/api/auth/remembered/forget", "/api/auth/register", "/api/auth/verification-code", "/api/auth/password-reset/code", "/api/auth/password-reset").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/admin/overview").hasAnyRole("ADMIN","PUBLIC_EDITOR","PUBLIC_ACTIVITY","PUBLIC_TECH")
                        .requestMatchers(HttpMethod.GET, "/api/admin/settings").hasAnyRole("ADMIN","PUBLIC_TECH")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/settings").hasAnyRole("ADMIN","PUBLIC_TECH")
                        .requestMatchers(HttpMethod.POST, "/api/admin/works/*/withdraw").hasAnyRole("ADMIN","PUBLIC_EDITOR")
                        .requestMatchers(HttpMethod.POST, "/api/admin/tags").hasAnyRole("ADMIN","PUBLIC_ACTIVITY")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new org.springframework.web.filter.OncePerRequestFilter(){
                    @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response,jakarta.servlet.FilterChain chain)throws jakarta.servlet.ServletException,java.io.IOException{
                        var context=org.springframework.security.core.context.SecurityContextHolder.getContext();var auth=context.getAuthentication();
                        Long actorId=null, publicId=null;
                        if(auth!=null&&auth.isAuthenticated()&&!(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)){
                            var session=request.getSession(false);String actorName=identities.actor(session,auth.getName());
                            var actor=store.one("SELECT id,role,password_hash,account_type FROM members WHERE username=?",actorName);
                            if(actor==null||session==null||!"PERSONAL".equals(actor.get("accountType"))||!java.util.Objects.equals(actor.get("passwordHash"),session.getAttribute("credentialHash"))){
                                context.setAuthentication(null);if(session!=null){identities.release(session);session.invalidate();}
                            } else {
                                actorId=((Number)actor.get("id")).longValue();String selected=identities.current(session,actorId,actorName);
                                String role=(String)actor.get("role");var roles=new java.util.ArrayList<org.springframework.security.core.GrantedAuthority>();
                                roles.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+role));
                                if("SUPER_ADMIN".equals(role))roles.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));
                                if(!"RESTRICTED".equals(role) && "haiyan_editor".equals(selected))roles.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PUBLIC_EDITOR"));
                                if(!"RESTRICTED".equals(role) && "haiyan_activity".equals(selected))roles.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PUBLIC_ACTIVITY"));
                                if(!"RESTRICTED".equals(role) && "haiyan_tech".equals(selected))roles.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PUBLIC_TECH"));
                                context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(selected,null,roles));
                                if(!selected.equals(actorName)){
                                    Object target=session.getAttribute(cn.haiyan.platform.service.IdentityService.SELECTED);if(target instanceof Number)publicId=((Number)target).longValue();
                                    String path=request.getRequestURI();String method=request.getMethod();
                                    if((path.equals("/api/member/profile")||path.equals("/api/member/profile/reminder-dismiss")||path.startsWith("/api/member/pen-names"))&&!"GET".equals(method)){
                                        response.sendError(403,"公用身份不支持修改个人资料");return;
                                    }
                                }
                            }
                        }
                        try {chain.doFilter(request,response);}
                        finally {
                            if(actorId!=null&&publicId!=null&&!java.util.Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())&&response.getStatus()<400){
                                try{String path=request.getRequestURI();if(path.length()>255)path=path.substring(0,255);store.update("INSERT INTO public_identity_audit(actor_member_id,public_member_id,action_method,action_path) VALUES(?,?,?,?)",actorId,publicId,request.getMethod(),path);}catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(SecurityConfig.class).error("Failed to record public identity action",ex);}
                            }
                        }
                    }                },org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .csrf(Customizer.withDefaults())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"请先登录\"}");
                        }))
                .build();
    }

    @Bean
    org.springframework.security.web.session.HttpSessionEventPublisher httpSessionEventPublisher(){return new org.springframework.security.web.session.HttpSessionEventPublisher();}

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(CommunityStore store) {
        return username -> {
            var m=store.one("SELECT * FROM members WHERE username=?",username);
            if(m==null)throw new UsernameNotFoundException("账号不存在");
            return User.withUsername(username).password((String)m.get("passwordHash")).roles((String)m.get("role")).build();
        };
    }
}

