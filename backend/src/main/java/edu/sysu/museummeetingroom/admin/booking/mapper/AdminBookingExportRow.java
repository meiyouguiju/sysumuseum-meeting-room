package edu.sysu.museummeetingroom.admin.booking.mapper;

import java.time.LocalDateTime;

public record AdminBookingExportRow(
        String bookingNo,
        String roomName,
        String organizerName,
        String subject,
        Integer attendeeCount,
        String participantsText,
        String description,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        LocalDateTime cancelledAt,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
