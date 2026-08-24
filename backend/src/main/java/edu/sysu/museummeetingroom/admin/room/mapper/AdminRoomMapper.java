package edu.sysu.museummeetingroom.admin.room.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminRoomMapper {

    @Insert("""
            INSERT INTO meeting_room(name, location, capacity, facilities_text, usage_notice,
                status, sort_order, created_by_user_id, updated_by_user_id)
            VALUES (#{name}, #{location}, #{capacity}, #{facilitiesText}, #{usageNotice},
                #{status}, #{sortOrder}, #{actorUserId}, #{actorUserId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AdminRoomEntity room);

    @Select("SELECT COUNT(*) FROM meeting_room WHERE name = #{name} AND id != #{excludedId}")
    int countByNameExcludingId(@Param("name") String name, @Param("excludedId") long excludedId);

    @Update("""
            UPDATE meeting_room
            SET name = #{name}, location = #{location}, capacity = #{capacity},
                facilities_text = #{facilitiesText}, usage_notice = #{usageNotice},
                sort_order = #{sortOrder}, updated_by_user_id = #{actorUserId}
            WHERE id = #{id}
            """)
    int update(AdminRoomEntity room);

    @Update("""
            UPDATE meeting_room
            SET status = #{status}, updated_by_user_id = #{actorUserId}
            WHERE id = #{roomId}
            """)
    int updateStatus(@Param("roomId") long roomId, @Param("status") String status,
            @Param("actorUserId") long actorUserId);
}
