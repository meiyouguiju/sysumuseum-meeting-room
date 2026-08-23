package edu.sysu.museummeetingroom.maintenance;

import java.time.LocalDateTime;

public record MaintenanceIdempotencyRecord(
        Long id,
        String operationType,
        Long userId,
        String processingStatus,
        LocalDateTime processingStartedAt,
        LocalDateTime expiresAt) {
}
