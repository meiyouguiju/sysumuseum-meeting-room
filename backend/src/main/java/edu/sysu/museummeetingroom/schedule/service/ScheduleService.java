package edu.sysu.museummeetingroom.schedule.service;

import edu.sysu.museummeetingroom.common.config.TimeConfiguration;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import edu.sysu.museummeetingroom.room.mapper.MeetingRoomMapper;
import edu.sysu.museummeetingroom.room.mapper.RoomRow;
import edu.sysu.museummeetingroom.schedule.dto.ScheduleResponse;
import edu.sysu.museummeetingroom.schedule.mapper.ScheduleBookingRow;
import edu.sysu.museummeetingroom.schedule.mapper.ScheduleMapper;
import edu.sysu.museummeetingroom.schedule.mapper.UnavailableSlotRow;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final MeetingRoomMapper meetingRoomMapper;
    private final ScheduleMapper scheduleMapper;
    private final Clock businessClock;

    public ScheduleResponse getSchedule(LocalDate date) {
        LocalDate today = LocalDate.now(businessClock);
        if (date.isAfter(today.plusDays(13))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_ERROR", "查询日期超出未来14天范围。");
        }
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime nextDayStart = date.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now(businessClock);
        List<RoomRow> rooms = meetingRoomMapper.findAllOrdered();
        List<ScheduleBookingRow> bookings = scheduleMapper.findActiveBookingsForDay(dayStart, nextDayStart);
        List<UnavailableSlotRow> heldSlots = scheduleMapper.findHeldSlotsForDay(dayStart, nextDayStart);
        return new ScheduleResponse(date, TimeConfiguration.BUSINESS_ZONE.getId(), 30,
                new ScheduleResponse.FocusWindow("08:30", "17:30"),
                rooms.stream().map(room -> new ScheduleResponse.ScheduleRoom(room.id(), room.name(), room.status(), room.capacity())).toList(),
                bookings.stream().map(booking -> toBooking(booking, now)).toList(),
                heldSlots.stream().map(slot -> new ScheduleResponse.UnavailableSlot(slot.roomId(), slot.slotStart(),
                        "CANCELLED_CURRENT_SLOT_HOLD")).toList());
    }

    private ScheduleResponse.ScheduleBooking toBooking(ScheduleBookingRow booking, LocalDateTime now) {
        String displayStatus = now.isBefore(booking.startTime()) ? "UPCOMING"
                : now.isBefore(booking.endTime()) ? "IN_PROGRESS" : "ENDED";
        return new ScheduleResponse.ScheduleBooking(booking.id(), booking.roomId(), booking.subject(), booking.organizerName(),
                booking.startTime(), booking.endTime(), displayStatus);
    }
}
