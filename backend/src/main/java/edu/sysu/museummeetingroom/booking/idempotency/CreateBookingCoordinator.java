package edu.sysu.museummeetingroom.booking.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sysu.museummeetingroom.auth.CurrentUser;
import edu.sysu.museummeetingroom.auth.CurrentUserProvider;
import edu.sysu.museummeetingroom.booking.command.CreateBookingCommand;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import java.util.Arrays;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateBookingCoordinator {

    private static final Set<String> DETERMINISTIC_FAILURE_CODES = Set.of(
            "MEETING_ROOM_NOT_FOUND",
            "MEETING_ROOM_DISABLED",
            "BOOKING_WINDOW_EXCEEDED",
            "BOOKING_TIME_INVALID",
            "BOOKING_CROSS_DAY_NOT_ALLOWED",
            "BOOKING_DURATION_EXCEEDED",
            "BOOKING_SLOT_CONFLICT");

    private final CurrentUserProvider currentUserProvider;
    private final CreateBookingRequestHasher createBookingRequestHasher;
    private final IdempotencyClaimService idempotencyClaimService;
    private final IdempotencyRecordQueryService idempotencyRecordQueryService;
    private final IdempotentBookingTransactionService idempotentBookingTransactionService;
    private final IdempotencyFailureService idempotencyFailureService;
    private final ObjectMapper objectMapper;

    public CreateBookingCoordinationResult create(CreateBookingCommand command, String idempotencyKey) {
        CurrentUser currentUser = requireActiveCurrentUser();
        byte[] requestHash = createBookingRequestHasher.hash(command);

        try {
            idempotencyClaimService.claim(currentUser.userId(), idempotencyKey, requestHash);
        } catch (DuplicateKeyException exception) {
            return readExistingResult(currentUser.userId(), idempotencyKey, requestHash);
        }

        try {
            return idempotentBookingTransactionService.createAndComplete(
                    currentUser.userId(),
                    idempotencyKey,
                    requestHash,
                    command);
        } catch (ApiException exception) {
            if (!DETERMINISTIC_FAILURE_CODES.contains(exception.errorCode())) {
                throw exception;
            }
            return idempotencyFailureService.fail(currentUser.userId(), idempotencyKey, exception);
        }
    }

    private CurrentUser requireActiveCurrentUser() {
        CurrentUser currentUser = currentUserProvider.currentUser();
        if (!"ACTIVE".equals(currentUser.userStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "当前用户不可用。");
        }
        return currentUser;
    }

    private CreateBookingCoordinationResult readExistingResult(
            Long userId,
            String idempotencyKey,
            byte[] requestHash) {
        IdempotencyRecord record = idempotencyRecordQueryService.findCreateBookingRecord(userId, idempotencyKey);
        if (record == null) {
            throw new IllegalStateException("领取冲突后未找到幂等记录");
        }
        if (!Arrays.equals(record.requestHash(), requestHash)) {
            return CreateBookingCoordinationResult.keyReused();
        }
        return switch (record.processingStatus()) {
            case "SUCCEEDED" -> CreateBookingCoordinationResult.replaySuccess(record, readResponseBody(record));
            case "FAILED" -> CreateBookingCoordinationResult.replayFailure(record, readResponseBody(record));
            case "PROCESSING" -> CreateBookingCoordinationResult.processing();
            default -> throw new IllegalStateException("未知幂等处理状态");
        };
    }

    private JsonNode readResponseBody(IdempotencyRecord record) {
        if (record.responseBody() == null) {
            throw new IllegalStateException("终态幂等记录缺少稳定响应");
        }
        try {
            return objectMapper.readTree(record.responseBody());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取幂等稳定响应", exception);
        }
    }
}
