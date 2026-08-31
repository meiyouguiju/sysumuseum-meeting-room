package edu.sysu.museummeetingroom.user.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMaintenanceMapper {

    @Insert("""
            INSERT INTO sys_user(auth_provider, external_subject, login_name, display_name, pin_hash, role_code, status)
            VALUES ('PIN_TRIAL', #{externalSubject}, #{loginName}, #{displayName}, #{pinHash}, #{roleCode}, 'ACTIVE')
            """)
    int insert(
            @Param("externalSubject") String externalSubject,
            @Param("loginName") String loginName,
            @Param("displayName") String displayName,
            @Param("pinHash") String pinHash,
            @Param("roleCode") String roleCode);

    @Update("UPDATE sys_user SET pin_hash = #{pinHash} WHERE id = #{id}")
    int updatePin(@Param("id") long id, @Param("pinHash") String pinHash);

    @Update("UPDATE sys_user SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") long id, @Param("status") String status);
}
