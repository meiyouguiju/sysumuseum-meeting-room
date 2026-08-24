package edu.sysu.museummeetingroom.admin.booking.mapper;

import java.time.LocalDateTime;

public record AdminBookingListRow(
        Long id,
        String bookingNo,
        Long roomId,
        String roomName,
        Long organizerUserId,
        String organizerName,
        String subject,
        Integer attendeeCount,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        Integer version,
        LocalDateTime cancelledAt) {
}
