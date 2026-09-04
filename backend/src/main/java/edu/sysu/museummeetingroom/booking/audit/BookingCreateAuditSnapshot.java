package edu.sysu.museummeetingroom.booking.audit;

import java.time.LocalDateTime;

public record BookingCreateAuditSnapshot(
        Long id,
        String bookingNo,
        Long roomId,
        Long organizerUserId,
        String organizerNameSnapshot,
        String subject,
        Integer attendeeCount,
        String participantsText,
        String description,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        Integer version,
        LocalDateTime createdAt) {}
