package edu.sysu.museummeetingroom.booking.idempotency;

import java.time.LocalDateTime;

public record IdempotencyRecord(
        Long id,
        String operationType,
        Long userId,
        String idempotencyKey,
        byte[] requestHash,
        String processingStatus,
        Long bookingId,
        Integer responseHttpStatus,
        String responseBody,
        String failureCode,
        LocalDateTime processingStartedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime updatedAt) {
}
