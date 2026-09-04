package edu.sysu.museummeetingroom.booking.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BookingMapper {

    @Insert("""
            INSERT INTO booking (
                booking_no, room_id, organizer_user_id, organizer_name_snapshot, subject,
                attendee_count, participants_text, description, start_time, end_time, status,
                version, last_modified_at, last_modified_by_user_id, created_at, updated_at
            ) VALUES (
                #{bookingNo}, #{roomId}, #{organizerUserId}, #{organizerNameSnapshot}, #{subject},
                #{attendeeCount}, #{participantsText}, #{description}, #{startTime}, #{endTime}, 'ACTIVE',
                1, #{occurredAt}, #{lastModifiedByUserId}, #{occurredAt}, #{occurredAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BookingEntity booking);

    @Update("""
            UPDATE booking
            SET booking_no = #{bookingNo},
                updated_at = #{occurredAt}
            WHERE id = #{bookingId}
            """)
    int updateBookingNoById(
            @Param("bookingId") Long bookingId,
            @Param("bookingNo") String bookingNo,
            @Param("occurredAt") LocalDateTime occurredAt);
}
