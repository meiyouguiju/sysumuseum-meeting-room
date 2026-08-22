package edu.sysu.museummeetingroom.booking.idempotency;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdempotencyClaimService {

    private final IdempotencyRecordMapper idempotencyRecordMapper;
    private final Clock businessClock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claim(Long userId, String idempotencyKey, byte[] requestHash) {
        LocalDateTime now = LocalDateTime.now(businessClock);
        idempotencyRecordMapper.insert(userId, idempotencyKey, requestHash, now, now.plusHours(24));
    }
}
