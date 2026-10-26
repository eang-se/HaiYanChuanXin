package cn.haiyan.platform.web;

import cn.haiyan.platform.mapper.PlatformMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SiteController {
    private final PlatformMapper platformMapper;

    public SiteController(PlatformMapper platformMapper) {
        this.platformMapper = platformMapper;
    }

    @GetMapping("/api/public/site")
    public SiteInfo site() {
        return new SiteInfo(platformMapper.getSetting("site_name"),
                platformMapper.getSetting("site_description"), "preparing");
    }

    public record SiteInfo(String name, String description, String status) {}
}

