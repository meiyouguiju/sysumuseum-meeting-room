package edu.sysu.museummeetingroom.maintenance;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SlotCleanupBatchTransactionService {

    private final SlotCleanupMapper slotCleanupMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteBatch(LocalDateTime cutoff, int batchSize) {
        return slotCleanupMapper.deletePastSlots(cutoff, batchSize);
    }
}
