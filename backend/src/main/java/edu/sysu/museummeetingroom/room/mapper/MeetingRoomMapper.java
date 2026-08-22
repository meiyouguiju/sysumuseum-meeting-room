package edu.sysu.museummeetingroom.room.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MeetingRoomMapper {
    @Select("""
            SELECT id, name, location, capacity, facilities_text, usage_notice, status, sort_order
            FROM meeting_room ORDER BY sort_order ASC, id ASC
            """)
    List<RoomRow> findAllOrdered();
}
