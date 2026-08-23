package edu.sysu.museummeetingroom.booking.idempotency;

import com.fasterxml.jackson.databind.JsonNode;

public record IdempotencyResult(
        String status,
        Integer originalHttpStatus,
        String failureCode,
        JsonNode response) {

    public static IdempotencyResult processing() {
        return new IdempotencyResult("PROCESSING", null, null, null);
    }
}
