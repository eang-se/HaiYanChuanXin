package cn.haiyan.platform.service;

import org.springframework.security.core.context.SecurityContextHolder;

/** ADMIN/SUPER_ADMIN authorities always describe the personal session actor. */
public final class ManagementAccess {
    private ManagementAccess() {}
    public static boolean has(String role){
        var auth=SecurityContextHolder.getContext().getAuthentication();
        return auth!=null&&auth.getAuthorities().stream().anyMatch(a->("ROLE_"+role).equals(a.getAuthority()));
    }
    public static boolean canReview(){return has("ADMIN")||has("SUPER_ADMIN")||has("PUBLIC_STAFF");}
    public static void requireAdmin(){if(!has("ADMIN")&&!has("SUPER_ADMIN"))throw CommunityService.error(403,"需要个人账号具有管理员权限");}
    public static void requireSuper(){if(!has("SUPER_ADMIN"))throw CommunityService.error(403,"需要个人账号具有超级管理员权限");}
}
