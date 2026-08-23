package edu.sysu.museummeetingroom.booking.query.dto;

import java.time.LocalDateTime;

public record BookingDetailResponse(
        Long id,
        String bookingNo,
        Room room,
        Organizer organizer,
        String subject,
        Integer attendeeCount,
        String participantsText,
        String description,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        String displayStatus,
        Integer version,
        LocalDateTime cancelledAt,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public record Room(Long id, String name) {
    }

    public record Organizer(Long id, String displayName) {
    }
}
