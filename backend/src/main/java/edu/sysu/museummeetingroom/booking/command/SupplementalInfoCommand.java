package edu.sysu.museummeetingroom.booking.command;

public record SupplementalInfoCommand(
        Integer version,
        Integer attendeeCount,
        String participantsText,
        String description) {

    public SupplementalInfoCommand {
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
