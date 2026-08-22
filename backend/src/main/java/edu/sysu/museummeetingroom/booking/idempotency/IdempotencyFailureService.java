package edu.sysu.museummeetingroom.booking.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdempotencyFailureService {

    private final IdempotencyRecordMapper idempotencyRecordMapper;
    private final ObjectMapper objectMapper;
    private final Clock businessClock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreateBookingCoordinationResult fail(
            Long userId,
            String idempotencyKey,
            ApiException exception) {
        IdempotencyRecord record = idempotencyRecordMapper.lock(userId, idempotencyKey);
        if (record == null || !"PROCESSING".equals(record.processingStatus())) {
            throw new IllegalStateException("幂等记录无法终结为失败状态");
        }

        String responseBody = writeFailureResponse(exception);
        LocalDateTime now = LocalDateTime.now(businessClock);
        int updatedRows = idempotencyRecordMapper.fail(
                record.id(),
                exception.status().value(),
                exception.errorCode(),
                responseBody,
                now);
        if (updatedRows != 1) {
            throw new IllegalStateException("幂等记录失败状态更新失败");
        }
        return CreateBookingCoordinationResult.firstFailure(
                exception.status().value(),
                exception.errorCode(),
                readResponseBody(responseBody));
    }

    private String writeFailureResponse(ApiException exception) {
        try {
            return objectMapper.writeValueAsString(
                    new StableFailureResponse(exception.errorCode(), exception.getMessage(), List.of()));
        } catch (JsonProcessingException serializationException) {
            throw new IllegalStateException("无法序列化首次预约失败响应", serializationException);
        }
    }

    private JsonNode readResponseBody(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取首次预约失败响应", exception);
        }
    }
}
