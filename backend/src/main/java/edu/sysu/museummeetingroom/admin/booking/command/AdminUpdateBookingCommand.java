package edu.sysu.museummeetingroom.admin.booking.command;

import java.time.LocalDateTime;

public record AdminUpdateBookingCommand(
        Integer version,
        Long roomId,
        String subject,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer attendeeCount,
        String participantsText,
        String description,
        String reason) {

    public AdminUpdateBookingCommand {
        subject = subject == null ? null : subject.strip();
        participantsText = normalizeOptionalText(participantsText);
        description = normalizeOptionalText(description);
        reason = normalizeOptionalText(reason);
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
