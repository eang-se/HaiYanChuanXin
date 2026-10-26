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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SiteController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {
    @Autowired MockMvc mvc;
    @MockitoBean PlatformMapper mapper;
    @MockitoBean cn.haiyan.platform.service.CommunityStore store;

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
}

