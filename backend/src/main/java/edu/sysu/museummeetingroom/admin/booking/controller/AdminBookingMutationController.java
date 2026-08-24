package edu.sysu.museummeetingroom.admin.booking.controller;

import edu.sysu.museummeetingroom.admin.booking.command.AdminUpdateBookingCommand;
import edu.sysu.museummeetingroom.admin.booking.service.AdminBookingUpdateService;
import edu.sysu.museummeetingroom.admin.booking.web.AdminUpdateBookingRequest;
import edu.sysu.museummeetingroom.booking.query.dto.BookingDetailResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminBookingMutationController {

    private final AdminBookingUpdateService adminBookingUpdateService;

    @PatchMapping("/api/v1/admin/bookings/{bookingId}")
    public BookingDetailResponse update(
            @PathVariable long bookingId,
            @Valid @RequestBody AdminUpdateBookingRequest request) {
        return adminBookingUpdateService.update(bookingId, new AdminUpdateBookingCommand(
                request.version(), request.roomId(), request.subject(), request.startTime(), request.endTime(),
                request.attendeeCount(), request.participantsText(), request.description(), request.reason()));
    }
}
