package edu.sysu.museummeetingroom.booking.controller;

import edu.sysu.museummeetingroom.booking.query.dto.BookingDetailResponse;
import edu.sysu.museummeetingroom.booking.query.dto.MyBookingsPageResponse;
import edu.sysu.museummeetingroom.booking.query.service.BookingQueryService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BookingQueryController {

    private final BookingQueryService bookingQueryService;

    @GetMapping("/api/v1/bookings/{bookingId}")
    public BookingDetailResponse getBookingDetail(@PathVariable long bookingId) {
        return bookingQueryService.getBookingDetail(bookingId);
    }

    @GetMapping("/api/v1/me/bookings")
    public MyBookingsPageResponse getMyBookings(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bookingQueryService.getMyBookings(page, size, status, date);
    }
}
