package edu.sysu.museummeetingroom.booking.query;

import edu.sysu.museummeetingroom.common.exception.ApiException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.http.HttpStatus;

public record BookingListFilter(
        String organizerKeyword,
        String status,
        LocalDateTime dayStart,
        LocalDateTime nextDayStart,
        LocalDateTime now) {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "UPCOMING", "IN_PROGRESS", "ENDED", "CANCELLED");

    public static BookingListFilter forMyBookings(String status, LocalDate date, LocalDateTime now) {
        return create(null, status, date, now);
    }

    public static BookingListFilter forAdminBookings(
            String organizerKeyword,
            String status,
            LocalDate date,
            LocalDateTime now) {
        return create(normalizeOrganizerKeyword(organizerKeyword), status, date, now);
    }

    private static BookingListFilter create(
            String organizerKeyword,
            String status,
            LocalDate date,
            LocalDateTime now) {
        if (status != null && !ALLOWED_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_ERROR", "预约状态筛选参数不合法。");
        }
        LocalDateTime dayStart = date == null ? null : date.atStartOfDay();
        LocalDateTime nextDayStart = date == null ? null : date.plusDays(1).atStartOfDay();
        return new BookingListFilter(organizerKeyword, status, dayStart, nextDayStart, now);
    }

    private static String normalizeOrganizerKeyword(String organizerKeyword) {
        if (organizerKeyword == null) {
            return null;
        }
        String normalized = organizerKeyword.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
