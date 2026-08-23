package edu.sysu.museummeetingroom.booking.query;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class BookingDisplayStatusResolver {

    public String resolve(String bookingStatus, LocalDateTime startTime, LocalDateTime endTime, LocalDateTime now) {
        if ("CANCELLED".equals(bookingStatus)) {
            return "CANCELLED";
        }
        if (now.isBefore(startTime)) {
            return "UPCOMING";
        }
        if (now.isBefore(endTime)) {
            return "IN_PROGRESS";
        }
        return "ENDED";
    }
}
