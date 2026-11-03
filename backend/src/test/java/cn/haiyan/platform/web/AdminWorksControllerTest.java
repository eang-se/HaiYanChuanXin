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

@WebMvcTest({AdminWorksController.class,CommunityController.class})
@Import(SecurityConfig.class)
class AdminWorksControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CommunityService service;
    @MockitoBean CommunityStore store;
    @MockitoBean IdentityService identities;
    @MockitoBean AccountPermissions permissions;
    @Test void publicBasicsAndPersonalAdministrativePowersAreIndependentOfIdentity() throws Exception {
        for(String role:List.of("MEMBER","RESTRICTED","ADMIN","SUPER_ADMIN"))for(String selected:List.of("actor","haiyan_official","haiyan_editor","haiyan_activity","haiyan_tech")){
            var session=identity(selected,role);boolean admin=Set.of("ADMIN","SUPER_ADMIN").contains(role),staff=!role.equals("RESTRICTED")&&!selected.equals("actor");
            mvc.perform(get("/api/admin/works").session(session).with(user("actor"))).andExpect(status().is(admin||staff?200:403));
            mvc.perform(post("/api/admin/works/55/approve").session(session).with(user("actor")).with(csrf())).andExpect(status().is(admin||staff?200:403));
            mvc.perform(post("/api/admin/works/55/withdraw").session(session).with(user("actor")).with(csrf())).andExpect(status().is(admin||staff?200:403));
            mvc.perform(delete("/api/admin/works/55").session(session).with(user("actor")).with(csrf())).andExpect(status().is(admin?200:403));
            mvc.perform(delete("/api/admin/activities/5").session(session).with(user("actor")).with(csrf())).andExpect(status().is(admin?200:403));
            mvc.perform(delete("/api/admin/accounts/2").session(session).with(user("actor")).with(csrf())).andExpect(status().is(role.equals("SUPER_ADMIN")?404:403));
            mvc.perform(put("/api/admin/public-accounts/29/members").session(session).with(user("actor")).with(csrf())).andExpect(status().is(role.equals("SUPER_ADMIN")?404:403));
        }
    }
    @Test void everyPublicIdentityCanPublishAndEndActivitiesButPersonalMembersCannot() throws Exception {
        when(service.store()).thenReturn(store);
        mvc.perform(get("/api/public/activities")).andExpect(status().isOk());
        when(store.update("UPDATE tags SET activity_active=? WHERE id=? AND kind='TOPIC' AND is_activity=TRUE",false,5L)).thenReturn(1);
        for(String selected:List.of("haiyan_official","haiyan_editor","haiyan_activity","haiyan_tech")){
            var session=identity(selected,"MEMBER");
            mvc.perform(post("/api/admin/tags").session(session).with(user("actor")).with(csrf()).contentType("application/json").content("{\"name\":\"征稿\",\"description\":\"\",\"isActivity\":true}")).andExpect(status().isOk());
            mvc.perform(put("/api/admin/activities/5/availability").session(session).with(user("actor")).with(csrf()).contentType("application/json").content("{\"active\":false}")).andExpect(status().isOk());
        }
        var session=identity("actor","MEMBER");
        mvc.perform(put("/api/admin/activities/5/availability").session(session).with(user("actor")).with(csrf()).contentType("application/json").content("{\"active\":false}")).andExpect(status().isForbidden());
    }

    private MockHttpSession identity(String selected,String role){
        var session=new MockHttpSession();session.setAttribute("credentialHash","hash");
        when(identities.actor(session,"actor")).thenReturn("actor");
        when(identities.current(session,1L,"actor")).thenReturn(selected);
        when(store.one("SELECT id,role,password_hash,account_type FROM members WHERE username=? AND deleted_at IS NULL","actor"))
            .thenReturn(Map.of("id",1L,"role",role,"passwordHash","hash","accountType","PERSONAL"));
        when(service.store()).thenReturn(store);
        when(store.one(startsWith("SELECT COUNT(*) AS total"))).thenReturn(Map.of("total",137L,"published",120L,"draft",12L,"withdrawn",5L,"featured",9L));
        return session;
    }
    @Test void queryIsParameterizedAndPaginationCoversOlderArticles() throws Exception {
        var session=identity("actor","ADMIN");
        when(store.count(contains("LEFT JOIN pen_names"),eq("%秋%"),eq("%秋%"),eq("%秋%"),eq("%秋%"),eq("%秋%"),eq("PUBLISHED"),eq("诗歌"))).thenReturn(137L);
        mvc.perform(get("/api/admin/works").session(session).with(user("actor")).param("q","秋").param("status","PUBLISHED").param("genre","诗歌").param("featured","true").param("page","10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(137)).andExpect(jsonPath("$.page").value(10)).andExpect(jsonPath("$.summary.published").value(120));
        verify(store).list(contains("w.status<>'DRAFT'"),eq("%秋%"),eq("%秋%"),eq("%秋%"),eq("%秋%"),eq("%秋%"),eq("PUBLISHED"),eq("诗歌"),eq(12),eq(108));
        verify(store).list(contains("ORDER BY w.updated_at DESC,w.id DESC LIMIT ? OFFSET ?"),eq("%秋%"),eq("%秋%"),eq("%秋%"),eq("%秋%"),eq("%秋%"),eq("PUBLISHED"),eq("诗歌"),eq(12),eq(108));
        verify(store).count(contains("AND EXISTS(SELECT 1 FROM work_tags"),eq("%秋%"),eq("%秋%"),eq("%秋%"),eq("%秋%"),eq("%秋%"),eq("PUBLISHED"),eq("诗歌"));
    }
    @Test void invalidFiltersAreRejectedAndEmptyPagesClampToValidPage() throws Exception {
        var session=identity("haiyan_editor","MEMBER");
        for(var parameter:Map.of("status","DRAFT","sort","DROP TABLE","page","-1","pageSize","1000","q","x".repeat(101)).entrySet())
            mvc.perform(get("/api/admin/works").session(session).with(user("actor")).param(parameter.getKey(),parameter.getValue())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/works").session(session).with(user("actor")).param("page","99"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1));
    }
    @Test void authorizedReviewCanReadUnpublishedBodyAndMissingWorkIsNotFound() throws Exception {
        var session=identity("haiyan_editor","MEMBER");
        var draft=new HashMap<String,Object>(Map.of("id",55L,"title","已下架稿件","status","WITHDRAWN","body","<p>正文</p>"));
        var rows=new ArrayList<Map<String,Object>>();rows.add(draft);
        when(store.list(CommunityService.WORK_SELECT+"WHERE w.id=? AND w.status<>'DRAFT'",55L)).thenReturn(rows);
        when(service.decorate(rows,true)).thenReturn(rows);
        mvc.perform(get("/api/admin/works/55").session(session).with(user("actor"))).andExpect(status().isOk()).andExpect(jsonPath("$.body").value("<p>正文</p>"));
        mvc.perform(get("/api/admin/works/404").session(session).with(user("actor"))).andExpect(status().isNotFound());
    }
}
