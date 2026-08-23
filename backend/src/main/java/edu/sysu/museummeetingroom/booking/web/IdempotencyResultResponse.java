package edu.sysu.museummeetingroom.booking.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdempotencyResultResponse(
        String status,
        Integer originalHttpStatus,
        String failureCode,
        JsonNode response) {
}
