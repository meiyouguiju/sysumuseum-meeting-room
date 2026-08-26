package edu.sysu.museummeetingroom.booking.query;

import edu.sysu.museummeetingroom.common.exception.ApiException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.http.HttpStatus;

public record BookingListFilter(
        String organizerKeyword,
        String status,
        LocalDateTime fromTime,
        LocalDateTime toTime,
        LocalDateTime now) {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "UPCOMING", "IN_PROGRESS", "ENDED", "CANCELLED");

    public static BookingListFilter forMyBookings(String status, LocalDate date, LocalDateTime now) {
        return create(null, status, date, null, null, now);
    }

    public static BookingListFilter forAdminBookings(
            String organizerKeyword,
            String status,
            LocalDate date,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDateTime now) {
        return create(normalizeOrganizerKeyword(organizerKeyword), status, date, fromDate, toDate, now);
    }

    private static BookingListFilter create(
            String organizerKeyword,
            String status,
            LocalDate date,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDateTime now) {
        if (status != null && !ALLOWED_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_ERROR", "预约状态筛选参数不合法。");
        }
        validateDateParameters(date, fromDate, toDate);
        LocalDateTime fromTime = date != null ? date.atStartOfDay() : fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime toTime = date != null
                ? date.plusDays(1).atStartOfDay()
                : toDate == null ? null : toDate.plusDays(1).atStartOfDay();
        return new BookingListFilter(organizerKeyword, status, fromTime, toTime, now);
    }

    private static void validateDateParameters(LocalDate date, LocalDate fromDate, LocalDate toDate) {
        if (date != null && (fromDate != null || toDate != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_ERROR", "date 不能与 fromDate 或 toDate 同时使用。");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_ERROR", "开始日期不能晚于结束日期。");
        }
    }

    private static String normalizeOrganizerKeyword(String organizerKeyword) {
        if (organizerKeyword == null) {
            return null;
        }
        String normalized = organizerKeyword.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
