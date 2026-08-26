package edu.sysu.museummeetingroom.booking.query.service;

import edu.sysu.museummeetingroom.auth.CurrentUser;
import edu.sysu.museummeetingroom.auth.CurrentUserProvider;
import edu.sysu.museummeetingroom.booking.query.BookingDisplayStatusResolver;
import edu.sysu.museummeetingroom.booking.query.BookingListFilter;
import edu.sysu.museummeetingroom.booking.query.dto.BookingDetailResponse;
import edu.sysu.museummeetingroom.booking.query.dto.MyBookingsPageResponse;
import edu.sysu.museummeetingroom.booking.query.mapper.BookingDetailRow;
import edu.sysu.museummeetingroom.booking.query.mapper.BookingQueryMapper;
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
public class BookingQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final BookingQueryMapper bookingQueryMapper;
    private final CurrentUserProvider currentUserProvider;
    private final BookingDisplayStatusResolver bookingDisplayStatusResolver;
    private final Clock businessClock;

    public BookingDetailResponse getBookingDetail(long bookingId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        BookingDetailRow booking = requireBooking(bookingId);
        if (!isOwnerOrAdmin(currentUser, booking)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BOOKING_ACCESS_DENIED", "无权查看该预约详情。");
        }
        return toResponse(booking, currentTime());
    }

    public MyBookingsPageResponse getMyBookings(Integer page, Integer size, String status, LocalDate date) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        validatePage(resolvedPage, resolvedSize);

        LocalDateTime now = currentTime();
        BookingListFilter filter = BookingListFilter.forMyBookings(status, date, now);
        long total = bookingQueryMapper.countByOrganizer(currentUser.userId(), filter);
        int totalPages = totalPages(total, resolvedSize);
        int offset = (resolvedPage - 1) * resolvedSize;
        List<BookingDetailResponse> items = bookingQueryMapper.findByOrganizer(
                        currentUser.userId(), filter, resolvedSize, offset)
                .stream()
                .map(booking -> toResponse(booking, now))
                .toList();
        return new MyBookingsPageResponse(items, resolvedPage, resolvedSize, total, totalPages);
    }

    private BookingDetailRow requireBooking(long bookingId) {
        BookingDetailRow booking = bookingQueryMapper.findById(bookingId);
        if (booking == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "预约不存在。");
        }
        return booking;
    }

    private boolean isOwnerOrAdmin(CurrentUser currentUser, BookingDetailRow booking) {
        return "ADMIN".equals(currentUser.roleCode())
                || currentUser.userId().equals(booking.organizerUserId());
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_ERROR", "分页参数不合法。");
        }
    }

    private int totalPages(long total, int size) {
        return (int) ((total + size - 1) / size);
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.now(businessClock);
    }

    private BookingDetailResponse toResponse(BookingDetailRow booking, LocalDateTime now) {
        return new BookingDetailResponse(
                booking.id(),
                booking.bookingNo(),
                new BookingDetailResponse.Room(booking.roomId(), booking.roomName()),
                new BookingDetailResponse.Organizer(booking.organizerUserId(), booking.organizerName()),
                booking.subject(),
                booking.attendeeCount(),
                booking.participantsText(),
                booking.description(),
                booking.startTime(),
                booking.endTime(),
                booking.status(),
                bookingDisplayStatusResolver.resolve(booking.status(), booking.startTime(), booking.endTime(), now),
                booking.version(),
                booking.cancelledAt(),
                booking.cancelReason(),
                booking.createdAt(),
                booking.updatedAt());
    }
}
