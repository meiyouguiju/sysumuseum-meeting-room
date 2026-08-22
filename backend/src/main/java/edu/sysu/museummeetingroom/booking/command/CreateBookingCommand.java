package edu.sysu.museummeetingroom.booking.command;

import java.time.LocalDateTime;

public record CreateBookingCommand(
        Long roomId,
        String subject,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer attendeeCount,
        String participantsText,
        String description) {

    public CreateBookingCommand {
        subject = normalizeRequiredText(subject);
        participantsText = normalizeOptionalText(participantsText);
        description = normalizeOptionalText(description);
    }

    private static String normalizeRequiredText(String value) {
        return value == null ? null : value.strip();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
