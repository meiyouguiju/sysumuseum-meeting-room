package edu.sysu.museummeetingroom.booking.mapper;

import java.time.LocalDateTime;

public record BookingSlotEntity(Long bookingId, Long roomId, LocalDateTime slotStart) {}
