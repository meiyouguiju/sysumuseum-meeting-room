package edu.sysu.museummeetingroom.maintenance;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SlotCleanupMapper {

    @Delete("""
            DELETE FROM booking_slot
            WHERE slot_start < #{cutoff}
            ORDER BY slot_start, id
            LIMIT #{batchSize}
            """)
    int deletePastSlots(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
