package edu.sysu.museummeetingroom.booking.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sysu.museummeetingroom.booking.command.CreateBookingCommand;
import edu.sysu.museummeetingroom.booking.dto.CreateBookingResult;
import edu.sysu.museummeetingroom.booking.service.BookingService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdempotentBookingTransactionService {

    private final IdempotencyRecordMapper idempotencyRecordMapper;
    private final BookingService bookingService;
    private final ObjectMapper objectMapper;
    private final Clock businessClock;

    @Transactional
    public CreateBookingCoordinationResult createAndComplete(
            Long userId,
            String idempotencyKey,
            byte[] requestHash,
            CreateBookingCommand command) {
        IdempotencyRecord record = idempotencyRecordMapper.lock(userId, idempotencyKey);
        verifyClaim(record, requestHash);

        CreateBookingResult bookingResult = bookingService.create(command);
        String responseBody = writeResponseBody(bookingResult);
        LocalDateTime now = LocalDateTime.now(businessClock);
        int updatedRows = idempotencyRecordMapper.succeed(record.id(), bookingResult.id(), responseBody, now);
        if (updatedRows != 1) {
            throw new IllegalStateException("幂等记录无法终结为成功状态");
        }
        return CreateBookingCoordinationResult.firstSuccess(bookingResult, readResponseBody(responseBody));
    }

    private void verifyClaim(IdempotencyRecord record, byte[] requestHash) {
        if (record == null) {
            throw new IllegalStateException("未找到已领取的幂等记录");
        }
        if (!Arrays.equals(record.requestHash(), requestHash)) {
            throw new IllegalStateException("幂等记录请求哈希不一致");
        }
        if (!"PROCESSING".equals(record.processingStatus())) {
            throw new IllegalStateException("幂等记录已被其他流程终结");
        }
    }

    private String writeResponseBody(CreateBookingResult bookingResult) {
        try {
            return objectMapper.writeValueAsString(bookingResult);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化首次预约成功响应", exception);
        }
    }

    private JsonNode readResponseBody(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取首次预约成功响应", exception);
        }
    }
}
