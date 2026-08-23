package edu.sysu.museummeetingroom.booking.command;

import java.time.LocalDateTime;

public record UpdateBookingCommand(
        Integer version,
        Long roomId,
        String subject,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer attendeeCount,
        String participantsText,
        String description) {

    public UpdateBookingCommand {
        subject = subject == null ? null : subject.strip();
        participantsText = normalizeOptionalText(participantsText);
        description = normalizeOptionalText(description);
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
