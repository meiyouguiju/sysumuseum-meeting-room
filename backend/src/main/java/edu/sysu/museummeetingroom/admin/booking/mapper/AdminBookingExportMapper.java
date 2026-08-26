package edu.sysu.museummeetingroom.admin.booking.mapper;

import edu.sysu.museummeetingroom.booking.query.BookingListFilter;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminBookingExportMapper {

    @Select("""
            <script>
            SELECT b.booking_no, r.name AS room_name,
                   b.organizer_name_snapshot AS organizer_name, b.subject,
                   b.attendee_count, b.participants_text, b.description,
                   b.start_time, b.end_time, b.status, b.cancelled_at,
                   b.cancel_reason, b.created_at, b.updated_at
            FROM booking b
            INNER JOIN meeting_room r ON r.id = b.room_id
            WHERE 1 = 1
            <if test="filter.fromTime != null">
              AND b.start_time >= #{filter.fromTime}
            </if>
            <if test="filter.toTime != null">
              AND b.start_time &lt; #{filter.toTime}
            </if>
            <if test="filter.organizerKeyword != null">
              AND b.organizer_name_snapshot LIKE CONCAT('%', #{filter.organizerKeyword}, '%')
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
            ORDER BY b.start_time ASC, b.id ASC
            </script>
            """)
    List<AdminBookingExportRow> findForExport(@Param("filter") BookingListFilter filter);
}
