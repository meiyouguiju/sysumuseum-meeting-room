package edu.sysu.museummeetingroom.booking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import edu.sysu.museummeetingroom.booking.command.CreateBookingCommand;
import edu.sysu.museummeetingroom.booking.idempotency.CreateBookingCoordinationResult;
import edu.sysu.museummeetingroom.booking.idempotency.CreateBookingCoordinator;
import edu.sysu.museummeetingroom.booking.idempotency.IdempotencyKeyValidator;
import edu.sysu.museummeetingroom.booking.idempotency.IdempotencyResult;
import edu.sysu.museummeetingroom.booking.idempotency.IdempotencyResultService;
import edu.sysu.museummeetingroom.booking.web.CreateBookingRequest;
import edu.sysu.museummeetingroom.booking.web.IdempotencyResultResponse;
import edu.sysu.museummeetingroom.common.api.ApiErrorResponse;
import edu.sysu.museummeetingroom.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final IdempotencyKeyValidator idempotencyKeyValidator;
    private final CreateBookingCoordinator createBookingCoordinator;
    private final IdempotencyResultService idempotencyResultService;

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request,
            HttpServletRequest servletRequest) {
        String validKey = idempotencyKeyValidator.requireValid(idempotencyKey);
        CreateBookingCoordinationResult result = createBookingCoordinator.create(toCommand(request), validKey);
        return toCreateResponse(result, servletRequest);
    }

    @GetMapping("/idempotency-result")
    public ResponseEntity<?> findIdempotencyResult(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String validKey = idempotencyKeyValidator.requireValid(idempotencyKey);
        IdempotencyResult result = idempotencyResultService.findCreateBookingResult(validKey);
        if ("PROCESSING".equals(result.status())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new IdempotencyResultResponse("PROCESSING", null, null, null));
        }
        return ResponseEntity.ok(new IdempotencyResultResponse(
                result.status(),
                result.originalHttpStatus(),
                result.failureCode(),
                result.response()));
    }

    private CreateBookingCommand toCommand(CreateBookingRequest request) {
        return new CreateBookingCommand(
                request.roomId(),
                request.subject(),
                request.startTime(),
                request.endTime(),
                request.attendeeCount(),
                request.participantsText(),
                request.description());
    }

    private ResponseEntity<?> toCreateResponse(
            CreateBookingCoordinationResult result,
            HttpServletRequest request) {
        return switch (result.status()) {
            case FIRST_SUCCESS, REPLAY_SUCCESS -> ResponseEntity
                    .status(result.originalHttpStatus())
                    .body(result.stableResponseBody());
            case FIRST_FAILURE, REPLAY_FAILURE -> ResponseEntity
                    .status(result.originalHttpStatus())
                    .body(toErrorResponse(result, request));
            case PROCESSING -> ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(toProcessingResponse(request));
            case KEY_REUSED -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ApiErrorResponse(
                            "IDEMPOTENCY_KEY_REUSED",
                            "Idempotency-Key不能用于不同的请求内容。",
                            List.of(),
                            RequestIdFilter.getRequestId(request)));
        };
    }

    private ApiErrorResponse toErrorResponse(
            CreateBookingCoordinationResult result,
            HttpServletRequest request) {
        JsonNode stableResponse = result.stableResponseBody();
        String message = stableResponse == null
                ? "预约创建失败。"
                : stableResponse.path("message").asText("预约创建失败。");
        return new ApiErrorResponse(
                result.failureCode(),
                message,
                List.of(),
                RequestIdFilter.getRequestId(request));
    }

    private ApiErrorResponse toProcessingResponse(HttpServletRequest request) {
        return new ApiErrorResponse(
                "IDEMPOTENCY_PROCESSING",
                "预约请求正在处理中。",
                List.of(),
                RequestIdFilter.getRequestId(request));
    }
}
