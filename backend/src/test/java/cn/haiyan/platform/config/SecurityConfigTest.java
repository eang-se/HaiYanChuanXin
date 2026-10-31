package cn.haiyan.platform.config;

import cn.haiyan.platform.mapper.PlatformMapper;
import cn.haiyan.platform.web.SiteController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import org.springframework.mock.web.MockHttpSession;
import java.util.Map;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SiteController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {
    @Autowired MockMvc mvc;
    @MockitoBean PlatformMapper mapper;
    @MockitoBean cn.haiyan.platform.service.CommunityStore store;
    @MockitoBean cn.haiyan.platform.service.IdentityService identities;

    @Test
    void publicSiteCanBeReadWithoutLogin() throws Exception {
        when(mapper.getSetting("site_name")).thenReturn("海燕传信");
        when(mapper.getSetting("site_description")).thenReturn("让文字在校园相遇");
        mvc.perform(get("/api/public/site"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("海燕传信"));
    }

    @Test
    void privateApiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/admin/environment"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
    @Test void draftDeletionRequiresLoginAndCsrf() throws Exception {
        mvc.perform(delete("/api/member/works/55").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/member/works/55").with(user("author"))).andExpect(status().isForbidden());
    }
    @Test
    void adminAccessComesFromThePersonalActorNotThePublicIdentity() throws Exception {
        var session=new MockHttpSession();session.setAttribute("actorUsername","alice");session.setAttribute("credentialHash","hash");session.setAttribute("identityId",10L);
        when(identities.actor(session,"shared")).thenReturn("alice");
        when(identities.current(session,1L,"alice")).thenReturn("shared");
        when(store.one("SELECT id,role,password_hash,account_type FROM members WHERE username=?","alice")).thenReturn(Map.of("id",1L,"role","MEMBER","passwordHash","hash","accountType","PERSONAL"));
        mvc.perform(get("/api/admin/environment").session(session).with(user("shared"))).andExpect(status().isForbidden());
        when(store.one("SELECT id,role,password_hash,account_type FROM members WHERE username=?","alice")).thenReturn(Map.of("id",1L,"role","SUPER_ADMIN","passwordHash","hash","accountType","PERSONAL"));
        mvc.perform(get("/api/admin/environment").session(session).with(user("shared"))).andExpect(status().isNotFound());
    }
    @Test
    void publicIdentityCannotEditPersonalProfile() throws Exception {
        var session=new MockHttpSession();session.setAttribute("actorUsername","alice");session.setAttribute("credentialHash","hash");session.setAttribute("identityId",10L);
        when(identities.actor(session,"shared")).thenReturn("alice");when(identities.current(session,1L,"alice")).thenReturn("shared");
        when(store.one("SELECT id,role,password_hash,account_type FROM members WHERE username=?","alice")).thenReturn(Map.of("id",1L,"role","MEMBER","passwordHash","hash","accountType","PERSONAL"));
        mvc.perform(put("/api/member/profile").session(session).with(user("shared")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put("/api/member/pen-names/5").session(session).with(user("shared")).with(csrf())).andExpect(status().isForbidden());
    }
    @Test
    void publicWorkspacesGrantOnlyTheirSpecificCapabilities() throws Exception {
        var session=new MockHttpSession();session.setAttribute("actorUsername","alice");session.setAttribute("credentialHash","hash");session.setAttribute("identityId",10L);
        when(identities.actor(session,"shared")).thenReturn("alice");
        when(store.one("SELECT id,role,password_hash,account_type FROM members WHERE username=?","alice")).thenReturn(Map.of("id",1L,"role","MEMBER","passwordHash","hash","accountType","PERSONAL"));
        when(identities.current(session,1L,"alice")).thenReturn("haiyan_editor");
        mvc.perform(get("/api/admin/overview").session(session).with(user("shared"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/works/1/withdraw").session(session).with(user("shared")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/tags").session(session).with(user("shared")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/settings").session(session).with(user("shared"))).andExpect(status().isForbidden());
        when(identities.current(session,1L,"alice")).thenReturn("haiyan_activity");
        mvc.perform(post("/api/admin/tags").session(session).with(user("shared")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/works/1/withdraw").session(session).with(user("shared")).with(csrf())).andExpect(status().isForbidden());
        when(identities.current(session,1L,"alice")).thenReturn("haiyan_tech");
        mvc.perform(get("/api/admin/settings").session(session).with(user("shared"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/settings").session(session).with(user("shared")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/accounts").session(session).with(user("shared"))).andExpect(status().isForbidden());
        when(store.one("SELECT id,role,password_hash,account_type FROM members WHERE username=?","alice")).thenReturn(Map.of("id",1L,"role","RESTRICTED","passwordHash","hash","accountType","PERSONAL"));
        mvc.perform(get("/api/admin/settings").session(session).with(user("shared"))).andExpect(status().isForbidden());
    }
}

