package edu.sysu.museummeetingroom.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper {
    @Select("SELECT id, display_name, department_name, role_code, status FROM sys_user WHERE id = #{id}")
    UserRow findById(long id);
}
