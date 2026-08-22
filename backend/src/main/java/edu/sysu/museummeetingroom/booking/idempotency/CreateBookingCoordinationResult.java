package edu.sysu.museummeetingroom.booking.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import edu.sysu.museummeetingroom.booking.dto.CreateBookingResult;

public record CreateBookingCoordinationResult(
        CreateBookingCoordinationStatus status,
        CreateBookingResult bookingResult,
        Integer originalHttpStatus,
        String failureCode,
        JsonNode stableResponseBody) {

    public static CreateBookingCoordinationResult firstSuccess(CreateBookingResult bookingResult, JsonNode stableResponseBody) {
        return new CreateBookingCoordinationResult(
                CreateBookingCoordinationStatus.FIRST_SUCCESS,
                bookingResult,
                201,
                null,
                stableResponseBody);
    }

    public static CreateBookingCoordinationResult replaySuccess(IdempotencyRecord record, JsonNode stableResponseBody) {
        return new CreateBookingCoordinationResult(
                CreateBookingCoordinationStatus.REPLAY_SUCCESS,
                null,
                record.responseHttpStatus(),
                null,
                stableResponseBody);
    }

    public static CreateBookingCoordinationResult firstFailure(
            int httpStatus,
            String failureCode,
            JsonNode stableResponseBody) {
        return new CreateBookingCoordinationResult(
                CreateBookingCoordinationStatus.FIRST_FAILURE,
                null,
                httpStatus,
                failureCode,
                stableResponseBody);
    }

    public static CreateBookingCoordinationResult replayFailure(IdempotencyRecord record, JsonNode stableResponseBody) {
        return new CreateBookingCoordinationResult(
                CreateBookingCoordinationStatus.REPLAY_FAILURE,
                null,
                record.responseHttpStatus(),
                record.failureCode(),
                stableResponseBody);
    }

    public static CreateBookingCoordinationResult processing() {
        return new CreateBookingCoordinationResult(
                CreateBookingCoordinationStatus.PROCESSING,
                null,
                null,
                null,
                null);
    }

    public static CreateBookingCoordinationResult keyReused() {
        return new CreateBookingCoordinationResult(
                CreateBookingCoordinationStatus.KEY_REUSED,
                null,
                null,
                "IDEMPOTENCY_KEY_REUSED",
                null);
    }
}
