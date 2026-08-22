package edu.sysu.museummeetingroom.common.api;

import java.util.List;

public record ApiErrorResponse(String errorCode, String message, List<FieldError> fieldErrors, String requestId) {
    public record FieldError(String field, String message) {}
}
