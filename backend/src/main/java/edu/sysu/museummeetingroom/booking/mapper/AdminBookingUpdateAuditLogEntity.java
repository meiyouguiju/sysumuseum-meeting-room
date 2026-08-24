package edu.sysu.museummeetingroom.booking.mapper;

import java.time.LocalDateTime;

public record AdminBookingUpdateAuditLogEntity(
        Long bookingId,
        Long actorUserId,
        String actorRoleSnapshot,
        Long targetOwnerUserId,
        String reason,
        Integer versionBefore,
        Integer versionAfter,
        String beforeJson,
        String afterJson,
        String slotChangeJson,
        LocalDateTime occurredAt) {
}
