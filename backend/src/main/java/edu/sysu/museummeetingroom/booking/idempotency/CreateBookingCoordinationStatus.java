package edu.sysu.museummeetingroom.booking.idempotency;

public enum CreateBookingCoordinationStatus {
    FIRST_SUCCESS,
    REPLAY_SUCCESS,
    FIRST_FAILURE,
    REPLAY_FAILURE,
    PROCESSING,
    KEY_REUSED
}
