package edu.sysu.museummeetingroom.maintenance;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SlotCleanupService {

    private final SlotTimeCalculator slotTimeCalculator;
    private final SlotCleanupBatchTransactionService slotCleanupBatchTransactionService;
    private final Clock businessClock;
    @Value("${maintenance.slot-cleanup.batch-size:500}")
    private int batchSize;

    public int cleanupPastSlots() {
        LocalDateTime cutoff = slotTimeCalculator.currentSlotStart(LocalDateTime.now(businessClock));
        int deletedCount = 0;
        int deletedInBatch;
        do {
            deletedInBatch = slotCleanupBatchTransactionService.deleteBatch(cutoff, batchSize);
            deletedCount += deletedInBatch;
        } while (deletedInBatch == batchSize);
        return deletedCount;
    }
}
