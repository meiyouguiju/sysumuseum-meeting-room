package edu.sysu.museummeetingroom.admin.booking.controller;

import edu.sysu.museummeetingroom.admin.booking.dto.AdminBookingsPageResponse;
import edu.sysu.museummeetingroom.admin.booking.service.AdminBookingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminBookingQueryController {

    private final AdminBookingQueryService adminBookingQueryService;

    @GetMapping("/api/v1/admin/bookings")
    public AdminBookingsPageResponse getBookings(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return adminBookingQueryService.getBookings(page, size);
    }
}
