package cn.haiyan.platform.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlatformMapper {
    @Select("SELECT setting_value FROM platform_metadata WHERE setting_key = #{key}")
    String getSetting(@Param("key") String key);
}

