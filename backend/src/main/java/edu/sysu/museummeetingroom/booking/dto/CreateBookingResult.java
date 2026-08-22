package edu.sysu.museummeetingroom.booking.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CreateBookingResult(
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
        Integer version,
        LocalDateTime createdAt,
        List<Warning> warnings) {

    public record Room(Long id, String name) {}

    public record Organizer(Long id, String displayName) {}

    public record Warning(String code, String message) {}
}
