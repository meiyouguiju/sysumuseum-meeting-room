package edu.sysu.museummeetingroom.admin.booking.mapper;

import edu.sysu.museummeetingroom.booking.query.BookingListFilter;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminBookingQueryMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM booking
            WHERE 1 = 1
            <if test="filter.organizerKeyword != null">
              AND organizer_name_snapshot LIKE CONCAT('%', #{filter.organizerKeyword}, '%')
            </if>
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
    long countAll(@Param("filter") BookingListFilter filter);

    @Select("""
            <script>
            SELECT b.id, b.booking_no, b.room_id, r.name AS room_name,
                   b.organizer_user_id, b.organizer_name_snapshot AS organizer_name,
                   b.subject, b.attendee_count, b.start_time, b.end_time,
                   b.status, b.version, b.cancelled_at
            FROM booking b
            INNER JOIN meeting_room r ON r.id = b.room_id
            WHERE 1 = 1
            <if test="filter.organizerKeyword != null">
              AND b.organizer_name_snapshot LIKE CONCAT('%', #{filter.organizerKeyword}, '%')
            </if>
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
    List<AdminBookingListRow> findPage(
            @Param("filter") BookingListFilter filter,
            @Param("limit") int limit,
            @Param("offset") long offset);
}
