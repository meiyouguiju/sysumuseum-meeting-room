package edu.sysu.museummeetingroom.booking.mapper;

import java.time.LocalDateTime;

public record BookingAuditLogEntity(
        Long bookingId,
        Long actorUserId,
        String actorRoleSnapshot,
        Long targetOwnerUserId,
        Integer versionAfter,
        String afterJson,
        String slotChangeJson,
        LocalDateTime occurredAt) {}
