package edu.sysu.museummeetingroom.booking.web;

import java.time.LocalDateTime;

public record CancelBookingResponse(Long id, String status, Integer version, LocalDateTime cancelledAt, SlotRelease slotRelease) {
    public record SlotRelease(String mode, LocalDateTime heldSlotStart, LocalDateTime releasedFrom) {
    }
}
