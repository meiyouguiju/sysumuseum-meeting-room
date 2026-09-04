package edu.sysu.museummeetingroom.booking.mapper;

import java.time.LocalDateTime;

public record BookingSupplementalInfoAuditLogEntity(
        Long bookingId,
        Long actorUserId,
        String actorRoleSnapshot,
        Long targetOwnerUserId,
        Integer versionBefore,
        Integer versionAfter,
        String beforeJson,
        String afterJson,
        LocalDateTime occurredAt) {
}
