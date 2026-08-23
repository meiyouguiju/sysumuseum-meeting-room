package edu.sysu.museummeetingroom.booking.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sysu.museummeetingroom.auth.CurrentUser;
import edu.sysu.museummeetingroom.auth.CurrentUserProvider;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdempotencyResultService {

    private final CurrentUserProvider currentUserProvider;
    private final IdempotencyRecordQueryService idempotencyRecordQueryService;
    private final ObjectMapper objectMapper;
    private final Clock businessClock;

    public IdempotencyResult findCreateBookingResult(String idempotencyKey) {
        CurrentUser currentUser = requireActiveCurrentUser();
        IdempotencyRecord record = idempotencyRecordQueryService.findCreateBookingRecord(
                currentUser.userId(),
                idempotencyKey);
        if (record == null || !record.expiresAt().isAfter(LocalDateTime.now(businessClock))) {
            throw notFoundOrExpired();
        }
        return switch (record.processingStatus()) {
            case "PROCESSING" -> IdempotencyResult.processing();
            case "SUCCEEDED" -> new IdempotencyResult(
                    "SUCCEEDED",
                    record.responseHttpStatus(),
                    null,
                    readStableResponse(record));
            case "FAILED" -> new IdempotencyResult(
                    "FAILED",
                    record.responseHttpStatus(),
                    record.failureCode(),
                    readStableResponse(record));
            default -> throw new IllegalStateException("未知幂等处理状态");
        };
    }

    private CurrentUser requireActiveCurrentUser() {
        CurrentUser currentUser = currentUserProvider.currentUser();
        if (!"ACTIVE".equals(currentUser.userStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "当前用户不可用。" );
        }
        return currentUser;
    }

    private ApiException notFoundOrExpired() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "IDEMPOTENCY_RESULT_NOT_FOUND_OR_EXPIRED",
                "未找到有效的幂等处理结果。" );
    }

    private JsonNode readStableResponse(IdempotencyRecord record) {
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
