package edu.sysu.museummeetingroom.schedule.mapper;

import java.time.LocalDateTime;

public record UnavailableSlotRow(Long roomId, LocalDateTime slotStart) {}
