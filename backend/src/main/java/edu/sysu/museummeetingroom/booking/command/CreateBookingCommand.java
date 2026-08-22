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
        subject = subject == null ? null : subject.trim();
    }
}
