package edu.sysu.museummeetingroom.booking.query.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BookingQueryMapper {

    @Select("""
            SELECT b.id, b.booking_no, b.room_id, r.name AS room_name,
                   b.organizer_user_id, b.organizer_name_snapshot AS organizer_name,
                   b.subject, b.attendee_count, b.participants_text, b.description,
                   b.start_time, b.end_time, b.status, b.version,
                   b.cancelled_at, b.cancel_reason, b.created_at, b.updated_at
            FROM booking b
            INNER JOIN meeting_room r ON r.id = b.room_id
            WHERE b.id = #{bookingId}
            """)
    BookingDetailRow findById(@Param("bookingId") long bookingId);

    @Select("""
            SELECT b.id, b.booking_no, b.room_id, r.name AS room_name,
                   b.organizer_user_id, b.organizer_name_snapshot AS organizer_name,
                   b.subject, b.attendee_count, b.participants_text, b.description,
                   b.start_time, b.end_time, b.status, b.version,
                   b.cancelled_at, b.cancel_reason, b.created_at, b.updated_at
            FROM booking b
            INNER JOIN meeting_room r ON r.id = b.room_id
            WHERE b.organizer_user_id = #{organizerUserId}
            ORDER BY b.start_time DESC, b.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<BookingDetailRow> findByOrganizer(
            @Param("organizerUserId") long organizerUserId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            SELECT COUNT(*)
            FROM booking
            WHERE organizer_user_id = #{organizerUserId}
            """)
    long countByOrganizer(@Param("organizerUserId") long organizerUserId);
}
