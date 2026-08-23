package edu.sysu.museummeetingroom.booking.service;

import edu.sysu.museummeetingroom.booking.command.CreateBookingCommand;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class BookingTimeRuleValidator {

    private static final int SLOT_MINUTES = 30;
    private static final Duration MAXIMUM_DURATION = Duration.ofHours(5);

    public void validate(CreateBookingCommand command, LocalDateTime now) {
        LocalDateTime startTime = command.startTime();
        LocalDateTime endTime = command.endTime();
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)
                || !isSlotBoundary(startTime) || !isSlotBoundary(endTime) || !startTime.isAfter(now)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BOOKING_TIME_INVALID", "预约时间不符合规则。");
        }
        if (!startTime.toLocalDate().equals(endTime.toLocalDate())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BOOKING_CROSS_DAY_NOT_ALLOWED", "预约不能跨自然日。");
        }
        LocalDate today = now.toLocalDate();
        if (startTime.toLocalDate().isAfter(today.plusDays(13))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BOOKING_WINDOW_EXCEEDED", "预约超出未来14天范围。");
        }
        Duration duration = Duration.between(startTime, endTime);
        if (duration.compareTo(Duration.ofMinutes(SLOT_MINUTES)) < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BOOKING_TIME_INVALID", "预约时长至少为30分钟。");
        }
        if (duration.compareTo(MAXIMUM_DURATION) > 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BOOKING_DURATION_EXCEEDED", "单次预约不能超过5小时。");
        }
    }

    private boolean isSlotBoundary(LocalDateTime time) {
        return time.getMinute() % SLOT_MINUTES == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }
}
