package edu.sysu.museummeetingroom.admin.booking.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminBookingQueryMapper {

    @Select("""
            SELECT COUNT(*)
            FROM booking
            """)
    long countAll();

    @Select("""
            SELECT b.id, b.booking_no, b.room_id, r.name AS room_name,
                   b.organizer_user_id, b.organizer_name_snapshot AS organizer_name,
                   b.subject, b.attendee_count, b.start_time, b.end_time,
                   b.status, b.version, b.cancelled_at
            FROM booking b
            INNER JOIN meeting_room r ON r.id = b.room_id
            ORDER BY b.start_time DESC, b.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<AdminBookingListRow> findPage(@Param("limit") int limit, @Param("offset") long offset);
}
