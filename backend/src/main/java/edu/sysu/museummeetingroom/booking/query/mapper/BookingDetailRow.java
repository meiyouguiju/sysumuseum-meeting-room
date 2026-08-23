package edu.sysu.museummeetingroom.booking.query.mapper;

import java.time.LocalDateTime;

public record BookingDetailRow(
        Long id,
        String bookingNo,
        Long roomId,
        String roomName,
        Long organizerUserId,
        String organizerName,
        String subject,
        Integer attendeeCount,
        String participantsText,
        String description,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        Integer version,
        LocalDateTime cancelledAt,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
