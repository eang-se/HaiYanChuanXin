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
    SecurityFilterChain filterChain(HttpSecurity http,CommunityStore store) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/favicon.svg",
                                "/api/public/**", "/api/auth/me", "/api/auth/csrf", "/api/auth/registration-options", "/api/media/**", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register", "/api/auth/verification-code").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new org.springframework.web.filter.OncePerRequestFilter(){
                    @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response,jakarta.servlet.FilterChain chain)throws jakarta.servlet.ServletException,java.io.IOException{
                        var context=org.springframework.security.core.context.SecurityContextHolder.getContext();var auth=context.getAuthentication();
                        if(auth!=null&&auth.isAuthenticated()&&!(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)){
                            var member=store.one("SELECT role FROM members WHERE username=?",auth.getName());
                            if(member==null)context.setAuthentication(null);
                            else context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(auth.getPrincipal(),null,java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+member.get("role")))));
                        }chain.doFilter(request,response);
                    }
                },org.springframework.security.web.access.intercept.AuthorizationFilter.class)
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

