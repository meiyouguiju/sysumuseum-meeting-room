package edu.sysu.museummeetingroom.booking.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdempotencyRecordQueryService {

    private final IdempotencyRecordMapper idempotencyRecordMapper;

    public IdempotencyRecord findCreateBookingRecord(Long userId, String idempotencyKey) {
        return idempotencyRecordMapper.find(userId, idempotencyKey);
    }
}
