package edu.sysu.museummeetingroom.maintenance;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdempotencyCleanupService {

    private final IdempotencyCleanupBatchTransactionService idempotencyCleanupBatchTransactionService;
    private final Clock businessClock;
    @Value("${maintenance.idempotency.cleanup-batch-size:500}")
    private int batchSize;

    public int cleanupExpiredTerminalRecords() {
        LocalDateTime now = LocalDateTime.now(businessClock);
        int deletedCount = 0;
        int deletedInBatch;
        do {
            deletedInBatch = idempotencyCleanupBatchTransactionService.deleteBatch(now, batchSize);
            deletedCount += deletedInBatch;
        } while (deletedInBatch == batchSize);
        return deletedCount;
    }
}
