package edu.sysu.museummeetingroom.legacyimport;

import java.time.LocalDateTime;

public record LegacyImportRecord(
        String sheetName,
        int sourceExcelRow,
        String bookingNo,
        String roomName,
        String organizerName,
        String subject,
        Integer attendeeCount,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String participantsText,
        String description) {
}
