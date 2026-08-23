package edu.sysu.museummeetingroom.booking.query.dto;

import java.util.List;

public record MyBookingsPageResponse(
        List<BookingDetailResponse> items,
        int page,
        int size,
        long total,
        int totalPages) {
}
