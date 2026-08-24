package edu.sysu.museummeetingroom.booking.mutation.mapper;

import edu.sysu.museummeetingroom.booking.command.UpdateBookingCommand;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BookingMutationMapper {

    @Update("""
            UPDATE booking SET status='CANCELLED', version=version+1, cancelled_at=#{occurredAt},
            cancelled_by_user_id=#{actorUserId}, cancel_reason=#{reason}, updated_at=#{occurredAt}
                , last_modified_at=#{occurredAt}, last_modified_by_user_id=#{actorUserId}
            WHERE id=#{bookingId} AND version=#{version} AND status='ACTIVE'
            """)
    int cancelWithVersion(@Param("bookingId") long bookingId, @Param("version") int version,
            @Param("actorUserId") long actorUserId, @Param("reason") String reason,
            @Param("occurredAt") LocalDateTime occurredAt);

    @Update("""
            UPDATE booking
            SET room_id = #{command.roomId}, subject = #{command.subject}, attendee_count = #{command.attendeeCount},
                participants_text = #{command.participantsText}, description = #{command.description},
                start_time = #{command.startTime}, end_time = #{command.endTime},
                version = version + 1, last_modified_at = #{occurredAt},
                last_modified_by_user_id = #{actorUserId}, updated_at = #{occurredAt}
            WHERE id = #{bookingId} AND version = #{version} AND status = 'ACTIVE'
            """)
    int updateWithVersion(
            @Param("bookingId") long bookingId,
            @Param("version") int version,
            @Param("command") UpdateBookingCommand command,
            @Param("actorUserId") long actorUserId,
            @Param("occurredAt") LocalDateTime occurredAt);
}
