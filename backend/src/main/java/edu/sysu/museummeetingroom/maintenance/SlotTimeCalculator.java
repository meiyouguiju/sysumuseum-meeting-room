package edu.sysu.museummeetingroom.maintenance;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class SlotTimeCalculator {

    private static final int SLOT_MINUTES = 30;

    public LocalDateTime currentSlotStart(LocalDateTime time) {
        return time.withMinute(time.getMinute() / SLOT_MINUTES * SLOT_MINUTES).withSecond(0).withNano(0);
    }
}
