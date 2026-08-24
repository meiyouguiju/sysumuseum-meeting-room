package edu.sysu.museummeetingroom.admin.booking.dto;

import java.time.LocalDateTime;

public record AdminBookingListItemResponse(
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
        String displayStatus,
        Integer version,
        LocalDateTime cancelledAt) {
}
