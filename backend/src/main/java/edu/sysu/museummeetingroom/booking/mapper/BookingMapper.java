package edu.sysu.museummeetingroom.booking.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

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
}
