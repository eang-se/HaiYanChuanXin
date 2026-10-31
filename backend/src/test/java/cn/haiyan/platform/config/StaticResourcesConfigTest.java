package cn.haiyan.platform.config;

import cn.haiyan.platform.mapper.PlatformMapper;
import cn.haiyan.platform.service.CommunityStore;
import cn.haiyan.platform.service.IdentityService;
import cn.haiyan.platform.web.SiteController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SiteController.class)
@Import({SecurityConfig.class, StaticResourcesConfig.class})
class StaticResourcesConfigTest {
    @Autowired MockMvc mvc;
    @MockitoBean PlatformMapper mapper;
    @MockitoBean CommunityStore store;
    @MockitoBean IdentityService identities;

    @Test void hashedAssetsArePubliclyCacheableAndSupportHeadAndRevalidation() throws Exception {
        var response=mvc.perform(get("/assets/cache-fixture-A1b2c3.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andReturn().getResponse();
        mvc.perform(head("/assets/cache-fixture-A1b2c3.js"))
                .andExpect(status().isOk()).andExpect(content().string(""));
        mvc.perform(get("/assets/cache-fixture-A1b2c3.js").header("If-Modified-Since",response.getHeader("Last-Modified")))
                .andExpect(status().isNotModified());
    }

    @Test void fixedBrandNamesHaveABoundedCacheLifetime() throws Exception {
        mvc.perform(get("/brand/cache-fixture.svg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=86400")))
                .andExpect(header().string("Cache-Control", containsString("must-revalidate")))
                .andExpect(header().string("Cache-Control", not(containsString("immutable"))));
    }

    @Test void apiAndMissingResourceResponsesDoNotReceiveStaticCacheHeaders() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        mvc.perform(get("/assets/missing-fixture.js"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", not(containsString("immutable"))));
    }

    @Test void headPermissionDoesNotExposePrivateApisOrPermitWrites() throws Exception {
        mvc.perform(head("/api/member/works")).andExpect(status().isUnauthorized());
        mvc.perform(post("/assets/cache-fixture-A1b2c3.js")).andExpect(status().isForbidden());
    }
}
