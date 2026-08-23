package edu.sysu.museummeetingroom.maintenance;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProcessingRecoveryService {

    private final MaintenanceIdempotencyMapper maintenanceIdempotencyMapper;
    private final ProcessingRecoveryTransactionService processingRecoveryTransactionService;
    private final Clock businessClock;
    @Value("${maintenance.idempotency.processing-recovery-threshold:PT2M}")
    private Duration recoveryThreshold;
    @Value("${maintenance.idempotency.processing-recovery-batch-size:100}")
    private int batchSize;

    public ProcessingRecoveryResult recoverStaleProcessingRecords() {
        LocalDateTime recoveryCutoff = LocalDateTime.now(businessClock).minus(recoveryThreshold);
        List<MaintenanceIdempotencyRecord> candidates = maintenanceIdempotencyMapper.findStaleProcessingCandidates(
                recoveryCutoff,
                batchSize);
        int recoveredCount = 0;
        for (MaintenanceIdempotencyRecord candidate : candidates) {
            if (processingRecoveryTransactionService.recoverIfStillStale(candidate.id(), recoveryCutoff)) {
                recoveredCount++;
            }
        }
        return new ProcessingRecoveryResult(candidates.size(), recoveredCount, candidates.size() - recoveredCount);
    }

    public record ProcessingRecoveryResult(int candidateCount, int recoveredCount, int skippedCount) {
    }
}
