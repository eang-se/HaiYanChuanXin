package cn.haiyan.platform.web;

import cn.haiyan.platform.config.SecurityConfig;
import cn.haiyan.platform.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({HomeController.class,TrafficController.class})
@Import(SecurityConfig.class)
class HomeControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CommunityService service;
    @MockitoBean CommunityStore store;
    @MockitoBean IdentityService identities;

    private MockHttpSession identity(String selected,String role) {
        var session=new MockHttpSession();session.setAttribute("credentialHash","hash");
        when(identities.actor(session,"actor")).thenReturn("actor");
        when(identities.current(session,1L,"actor")).thenReturn(selected);
        when(store.one("SELECT id,role,password_hash,account_type FROM members WHERE username=? AND deleted_at IS NULL","actor"))
            .thenReturn(Map.of("id",1L,"role",role,"passwordHash","hash","accountType","PERSONAL"));
        when(service.store()).thenReturn(store);
        when(store.one("SELECT status FROM works WHERE id=? FOR UPDATE",55L)).thenReturn(Map.of("status","PUBLISHED"));
        when(store.one("SELECT id FROM tags WHERE name='海燕精选' AND kind='TOPIC'")).thenReturn(Map.of("id",7L));
        return session;
    }
    @Test void allPublicIdentitiesAndPersonalAdministratorsCanAwardFeatured() throws Exception {
        for(String selected:List.of("actor")) {
            var session=identity(selected,"MEMBER");
            mvc.perform(put("/api/admin/works/55/featured").session(session).with(user("actor")).with(csrf()).contentType("application/json").content("{\"active\":true}"))
                .andExpect(status().isForbidden());
        }
        for(String role:List.of("MEMBER","ADMIN","SUPER_ADMIN")) {
            var session=identity(role.equals("MEMBER")?"haiyan_editor":"actor",role);
            mvc.perform(put("/api/admin/works/55/featured").session(session).with(user("actor")).with(csrf()).contentType("application/json").content("{\"active\":true}"))
                .andExpect(status().isOk());
        }
        verify(store,times(3)).update("INSERT IGNORE INTO work_tags(work_id,tag_id) VALUES(?,?)",55L,7L);
    }
    @Test void restrictedEditorAndCsrfLessRequestsAreRejected() throws Exception {
        var session=identity("haiyan_editor","RESTRICTED");
        mvc.perform(put("/api/admin/works/55/featured").session(session).with(user("actor")).with(csrf()).contentType("application/json").content("{\"active\":true}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/public/traffic")).andExpect(status().isForbidden());
    }
    @Test void draftsCannotBeFeaturedAndAwardCanBeRemoved() throws Exception {
        var session=identity("haiyan_editor","MEMBER");
        when(store.one("SELECT status FROM works WHERE id=? FOR UPDATE",55L)).thenReturn(Map.of("status","DRAFT"));
        mvc.perform(put("/api/admin/works/55/featured").session(session).with(user("actor")).with(csrf()).contentType("application/json").content("{\"active\":true}"))
            .andExpect(status().isNotFound());
        when(store.one("SELECT status FROM works WHERE id=? FOR UPDATE",55L)).thenReturn(Map.of("status","WITHDRAWN"));
        mvc.perform(put("/api/admin/works/55/featured").session(session).with(user("actor")).with(csrf()).contentType("application/json").content("{\"active\":false}"))
            .andExpect(status().isOk());
        verify(store).update("DELETE FROM work_tags WHERE work_id=? AND tag_id=?",55L,7L);
    }
    @Test void anonymousHeartbeatSetsPrivateCookieAndStoresOnlyHash() throws Exception {
        when(store.one(startsWith("SELECT COUNT(*) AS visitors"))).thenReturn(Map.of("online",2L,"visitors",18L));
        var result=mvc.perform(post("/api/public/traffic").with(csrf())).andExpect(status().isOk())
            .andExpect(jsonPath("$.online").value(2)).andExpect(jsonPath("$.visitors").value(18))
            .andExpect(header().string("Cache-Control","no-store")).andReturn();
        var cookie=result.getResponse().getCookie("haiyan_visitor");
        org.junit.jupiter.api.Assertions.assertNotNull(cookie);
        org.junit.jupiter.api.Assertions.assertTrue(cookie.isHttpOnly());
        mvc.perform(post("/api/public/traffic").cookie(cookie).with(csrf())).andExpect(status().isOk())
            .andExpect(header().doesNotExist("Set-Cookie"));
        verify(store,times(2)).update("INSERT INTO site_visitors(visitor_hash) VALUES(?) ON DUPLICATE KEY UPDATE last_seen=CURRENT_TIMESTAMP",AuthController.hash(cookie.getValue()));
    }
    @Test void randomEndpointValidatesInputAndOmitsPreviousBatch() throws Exception {
        when(service.store()).thenReturn(store);
        when(store.count("SELECT COUNT(*) FROM works WHERE status='PUBLISHED'")).thenReturn(9L);
        mvc.perform(get("/api/public/works/random").param("exclude","1,2,3")).andExpect(status().isOk());
        verify(store).list(contains("w.status='PUBLISHED' AND w.id NOT IN (?,?,?)"),eq(1L),eq(2L),eq(3L));
        mvc.perform(get("/api/public/works/random").param("exclude","1) OR 1=1")).andExpect(status().isBadRequest());
    }
}
