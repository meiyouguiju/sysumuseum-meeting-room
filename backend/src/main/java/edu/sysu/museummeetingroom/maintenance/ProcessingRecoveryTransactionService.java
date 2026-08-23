package edu.sysu.museummeetingroom.maintenance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProcessingRecoveryTransactionService {

    private final MaintenanceIdempotencyMapper maintenanceIdempotencyMapper;
    private final ObjectMapper objectMapper;
    private final Clock businessClock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recoverIfStillStale(Long id, LocalDateTime recoveryCutoff) {
        MaintenanceIdempotencyRecord record = maintenanceIdempotencyMapper.lockById(id);
        if (!isRecoverable(record, recoveryCutoff)) {
            return false;
        }
        int updatedRows = maintenanceIdempotencyMapper.recoverAsInternalError(
                record.id(),
                stableInternalErrorResponse(),
                LocalDateTime.now(businessClock));
        return updatedRows == 1;
    }

    private boolean isRecoverable(MaintenanceIdempotencyRecord record, LocalDateTime recoveryCutoff) {
        return record != null
                && "CREATE_BOOKING".equals(record.operationType())
                && "PROCESSING".equals(record.processingStatus())
                && record.processingStartedAt().isBefore(recoveryCutoff);
    }

    private String stableInternalErrorResponse() {
        try {
            return objectMapper.writeValueAsString(new StableInternalErrorResponse(
                    "INTERNAL_ERROR",
                    "服务暂时无法处理请求。",
                    List.of()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化幂等恢复失败响应", exception);
        }
    }

    private record StableInternalErrorResponse(String errorCode, String message, List<FieldError> fieldErrors) {

        private record FieldError(String field, String message) {
        }
    }
}
