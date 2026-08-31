package edu.sysu.museummeetingroom.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysUserMapper {
    @Select("SELECT id, display_name, pin_hash, department_name, role_code, status FROM sys_user WHERE id = #{id}")
    UserRow findById(long id);

    @Select("""
            SELECT id, display_name, pin_hash, department_name, role_code, status
            FROM sys_user
            WHERE display_name = #{displayName}
              AND status = 'ACTIVE'
              AND pin_hash IS NOT NULL
            ORDER BY id
            """)
    List<UserRow> findActiveByDisplayName(@Param("displayName") String displayName);

    @Select("""
            SELECT id, display_name, pin_hash, department_name, role_code, status
            FROM sys_user
            WHERE id = #{id}
              AND status = 'ACTIVE'
            """)
    UserRow findActiveById(@Param("id") long id);
}
