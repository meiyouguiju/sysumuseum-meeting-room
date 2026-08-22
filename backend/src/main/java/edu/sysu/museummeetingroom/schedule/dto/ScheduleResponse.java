package edu.sysu.museummeetingroom.schedule.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ScheduleResponse(LocalDate date, String timeZone, int slotMinutes, FocusWindow focusWindow,
        List<ScheduleRoom> rooms, List<ScheduleBooking> bookings, List<UnavailableSlot> unavailableSlots) {
    public record FocusWindow(String start, String end) {}
    public record ScheduleRoom(Long id, String name, String status, Integer capacity) {}
    public record ScheduleBooking(Long id, Long roomId, String subject, String organizerName,
            LocalDateTime startTime, LocalDateTime endTime, String displayStatus) {}
    public record UnavailableSlot(Long roomId, LocalDateTime slotStart, String reason) {}
}
