package edu.sysu.museummeetingroom.admin.booking.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminBookingExportMapper {

    @Select("""
            SELECT b.booking_no, r.name AS room_name,
                   b.organizer_name_snapshot AS organizer_name, b.subject,
                   b.attendee_count, b.participants_text, b.description,
                   b.start_time, b.end_time, b.status, b.cancelled_at,
                   b.cancel_reason, b.created_at, b.updated_at
            FROM booking b
            INNER JOIN meeting_room r ON r.id = b.room_id
            WHERE b.start_time >= #{fromTime}
              AND b.start_time <= #{toTime}
            ORDER BY b.start_time ASC, b.id ASC
            """)
    List<AdminBookingExportRow> findForExport(
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime);
}
