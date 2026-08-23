package edu.sysu.museummeetingroom.maintenance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class MaintenanceScheduler {

    private final SlotCleanupService slotCleanupService;
    private final ProcessingRecoveryService processingRecoveryService;
    private final IdempotencyCleanupService idempotencyCleanupService;

    @Scheduled(cron = "${maintenance.slot-cleanup.cron:0 2,32 * * * *}", zone = "Asia/Shanghai")
    public void cleanupPastSlots() {
        try {
            int deletedCount = slotCleanupService.cleanupPastSlots();
            log.info("Slot cleanup completed, deletedCount={}", deletedCount);
        } catch (Exception exception) {
            log.error("Slot cleanup failed", exception);
        }
    }

    @Scheduled(cron = "${maintenance.idempotency.processing-recovery-cron:0 * * * * *}", zone = "Asia/Shanghai")
    public void recoverStaleProcessingRecords() {
        try {
            ProcessingRecoveryService.ProcessingRecoveryResult result = processingRecoveryService.recoverStaleProcessingRecords();
            log.info(
                    "Processing recovery completed, candidateCount={}, recoveredCount={}, skippedCount={}",
                    result.candidateCount(),
                    result.recoveredCount(),
                    result.skippedCount());
        } catch (Exception exception) {
            log.error("Processing recovery failed", exception);
        }
    }

    @Scheduled(cron = "${maintenance.idempotency.cleanup-cron:0 17 * * * *}", zone = "Asia/Shanghai")
    public void cleanupExpiredIdempotencyRecords() {
        try {
            int deletedCount = idempotencyCleanupService.cleanupExpiredTerminalRecords();
            log.info("Idempotency cleanup completed, deletedCount={}", deletedCount);
        } catch (Exception exception) {
            log.error("Idempotency cleanup failed", exception);
        }
    }
}
