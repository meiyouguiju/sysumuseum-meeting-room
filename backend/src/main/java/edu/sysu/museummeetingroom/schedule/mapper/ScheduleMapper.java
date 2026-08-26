package edu.sysu.museummeetingroom.schedule.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScheduleMapper {
    @Select("""
            SELECT id, room_id, organizer_user_id, subject, organizer_name_snapshot AS organizer_name, start_time, end_time
            FROM booking
            WHERE status = 'ACTIVE' AND start_time >= #{dayStart} AND start_time < #{nextDayStart}
            ORDER BY start_time ASC, room_id ASC, id ASC
            """)
    List<ScheduleBookingRow> findActiveBookingsForDay(LocalDateTime dayStart, LocalDateTime nextDayStart);

    @Select("""
            SELECT room_id, slot_start
            FROM booking_slot
            WHERE occupancy_state = 'CANCELLED_CURRENT_SLOT_HOLD'
              AND slot_start >= #{dayStart} AND slot_start < #{nextDayStart}
            ORDER BY slot_start ASC, room_id ASC
            """)
    List<UnavailableSlotRow> findHeldSlotsForDay(LocalDateTime dayStart, LocalDateTime nextDayStart);
}
