package edu.sysu.museummeetingroom.admin.booking.service;

import edu.sysu.museummeetingroom.admin.booking.dto.AdminBookingListItemResponse;
import edu.sysu.museummeetingroom.admin.booking.dto.AdminBookingsPageResponse;
import edu.sysu.museummeetingroom.admin.booking.mapper.AdminBookingListRow;
import edu.sysu.museummeetingroom.admin.booking.mapper.AdminBookingQueryMapper;
import edu.sysu.museummeetingroom.auth.CurrentUser;
import edu.sysu.museummeetingroom.auth.CurrentUserProvider;
import edu.sysu.museummeetingroom.booking.query.BookingDisplayStatusResolver;
import edu.sysu.museummeetingroom.booking.query.BookingListFilter;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminBookingQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final CurrentUserProvider currentUserProvider;
    private final AdminBookingQueryMapper adminBookingQueryMapper;
    private final BookingDisplayStatusResolver bookingDisplayStatusResolver;
    private final Clock businessClock;

    public AdminBookingsPageResponse getBookings(
            Integer page,
            Integer size,
            String organizerKeyword,
            LocalDate date,
            String status) {
        requireAdmin();
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        validatePage(resolvedPage, resolvedSize);

        LocalDateTime now = LocalDateTime.now(businessClock);
        BookingListFilter filter = BookingListFilter.forAdminBookings(organizerKeyword, status, date, now);
        long total = adminBookingQueryMapper.countAll(filter);
        long offset = ((long) resolvedPage - 1) * resolvedSize;
        List<AdminBookingListItemResponse> items = adminBookingQueryMapper.findPage(filter, resolvedSize, offset)
                .stream()
                .map(row -> toResponse(row, now))
                .toList();
        return new AdminBookingsPageResponse(items, resolvedPage, resolvedSize, total, totalPages(total, resolvedSize));
    }

    private void requireAdmin() {
        CurrentUser currentUser = currentUserProvider.currentUser();
        if (!"ADMIN".equals(currentUser.roleCode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅管理员可查看预约列表。");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_ERROR", "分页参数不合法。");
        }
    }

    private int totalPages(long total, int size) {
        return (int) ((total + size - 1) / size);
    }

    private AdminBookingListItemResponse toResponse(AdminBookingListRow row, LocalDateTime now) {
        return new AdminBookingListItemResponse(
                row.id(),
                row.bookingNo(),
                row.roomId(),
                row.roomName(),
                row.organizerUserId(),
                row.organizerName(),
                row.subject(),
                row.attendeeCount(),
                row.startTime(),
                row.endTime(),
                row.status(),
                bookingDisplayStatusResolver.resolve(row.status(), row.startTime(), row.endTime(), now),
                row.version(),
                row.cancelledAt());
    }
}
