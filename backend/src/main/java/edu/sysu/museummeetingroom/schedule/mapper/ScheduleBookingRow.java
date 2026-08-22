package edu.sysu.museummeetingroom.schedule.mapper;

import java.time.LocalDateTime;

public record ScheduleBookingRow(Long id, Long roomId, String subject, String organizerName,
        LocalDateTime startTime, LocalDateTime endTime) {}
