package edu.sysu.museummeetingroom.maintenance;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdempotencyCleanupBatchTransactionService {

    private final MaintenanceIdempotencyMapper maintenanceIdempotencyMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteBatch(LocalDateTime now, int batchSize) {
        return maintenanceIdempotencyMapper.deleteExpiredTerminalRecords(now, batchSize);
    }
}
