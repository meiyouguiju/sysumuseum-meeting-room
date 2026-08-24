package edu.sysu.museummeetingroom.admin.booking.dto;

import java.util.List;

public record AdminBookingsPageResponse(
        List<AdminBookingListItemResponse> items,
        int page,
        int size,
        long total,
        int totalPages) {
}
