package edu.sysu.museummeetingroom.booking.idempotency;

import java.util.List;

record StableFailureResponse(String errorCode, String message, List<FieldError> fieldErrors) {

    record FieldError(String field, String message) {
    }
}
