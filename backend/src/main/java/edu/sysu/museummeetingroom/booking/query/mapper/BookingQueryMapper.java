package edu.sysu.museummeetingroom.booking.query.mapper;

import edu.sysu.museummeetingroom.booking.query.BookingListFilter;
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
            WHERE b.id = #{bookingId}
            FOR UPDATE
            """)
    BookingDetailRow findByIdForUpdate(@Param("bookingId") long bookingId);

    @Select("""
            <script>
            SELECT b.id, b.booking_no, b.room_id, r.name AS room_name,
                   b.organizer_user_id, b.organizer_name_snapshot AS organizer_name,
                   b.subject, b.attendee_count, b.participants_text, b.description,
                   b.start_time, b.end_time, b.status, b.version,
                   b.cancelled_at, b.cancel_reason, b.created_at, b.updated_at
            FROM booking b
            INNER JOIN meeting_room r ON r.id = b.room_id
            WHERE b.organizer_user_id = #{organizerUserId}
            <if test="filter.dayStart != null">
              AND b.start_time >= #{filter.dayStart} AND b.start_time &lt; #{filter.nextDayStart}
            </if>
            <choose>
              <when test="filter.status == 'CANCELLED'">
                AND b.status = 'CANCELLED'
              </when>
              <when test="filter.status == 'UPCOMING'">
                AND b.status = 'ACTIVE' AND b.start_time > #{filter.now}
              </when>
              <when test="filter.status == 'IN_PROGRESS'">
                AND b.status = 'ACTIVE' AND b.start_time &lt;= #{filter.now} AND b.end_time > #{filter.now}
              </when>
              <when test="filter.status == 'ENDED'">
                AND b.status = 'ACTIVE' AND b.end_time &lt;= #{filter.now}
              </when>
            </choose>
            ORDER BY b.start_time DESC, b.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<BookingDetailRow> findByOrganizer(
            @Param("organizerUserId") long organizerUserId,
            @Param("filter") BookingListFilter filter,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM booking
            WHERE organizer_user_id = #{organizerUserId}
            <if test="filter.dayStart != null">
              AND start_time >= #{filter.dayStart} AND start_time &lt; #{filter.nextDayStart}
            </if>
            <choose>
              <when test="filter.status == 'CANCELLED'">
                AND status = 'CANCELLED'
              </when>
              <when test="filter.status == 'UPCOMING'">
                AND status = 'ACTIVE' AND start_time > #{filter.now}
              </when>
              <when test="filter.status == 'IN_PROGRESS'">
                AND status = 'ACTIVE' AND start_time &lt;= #{filter.now} AND end_time > #{filter.now}
              </when>
              <when test="filter.status == 'ENDED'">
                AND status = 'ACTIVE' AND end_time &lt;= #{filter.now}
              </when>
            </choose>
            </script>
            """)
    long countByOrganizer(@Param("organizerUserId") long organizerUserId, @Param("filter") BookingListFilter filter);
}
