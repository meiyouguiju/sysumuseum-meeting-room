package edu.sysu.museummeetingroom.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
            WHERE display_name = #{displayName}
              AND status = 'ACTIVE'
              AND pin_hash IS NOT NULL
            ORDER BY id
            FOR UPDATE
            """)
    List<UserRow> findActiveByDisplayNameForUpdate(@Param("displayName") String displayName);

    @Select("""
            SELECT id, display_name, pin_hash, department_name, role_code, status
            FROM sys_user
            WHERE id = #{id}
              AND status = 'ACTIVE'
            """)
    UserRow findActiveById(@Param("id") long id);

    @Update("""
            UPDATE sys_user
            SET pin_hash = #{pinHash}
            WHERE id = #{id}
              AND status = 'ACTIVE'
            """)
    int updateActivePin(@Param("id") long id, @Param("pinHash") String pinHash);
}
