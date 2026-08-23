package edu.sysu.museummeetingroom.booking.service;

import edu.sysu.museummeetingroom.booking.mapper.BookingSlotEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BookingSlotGenerator {

    private static final int SLOT_MINUTES = 30;

    public List<BookingSlotEntity> generate(Long bookingId, Long roomId, LocalDateTime startTime, LocalDateTime endTime) {
        List<BookingSlotEntity> slots = new ArrayList<>();
        for (LocalDateTime slotStart = startTime; slotStart.isBefore(endTime); slotStart = slotStart.plusMinutes(SLOT_MINUTES)) {
            slots.add(new BookingSlotEntity(bookingId, roomId, slotStart));
        }
        return slots;
    }
}
