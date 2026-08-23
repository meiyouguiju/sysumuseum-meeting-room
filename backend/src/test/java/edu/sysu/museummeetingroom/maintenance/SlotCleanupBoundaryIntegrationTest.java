package edu.sysu.museummeetingroom.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(SlotCleanupBoundaryIntegrationTest.FixedClockConfiguration.class)
class SlotCleanupBoundaryIntegrationTest {

    private final SlotCleanupService slotCleanupService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    SlotCleanupBoundaryIntegrationTest(SlotCleanupService slotCleanupService, JdbcTemplate jdbcTemplate) {
        this.slotCleanupService = slotCleanupService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM booking_slot WHERE id BETWEEN 887001 AND 887003");
        jdbcTemplate.update("""
                INSERT INTO booking_slot(id, booking_id, room_id, slot_start, occupancy_state)
                VALUES (887001, 888001, 889001, '2026-08-23 10:00:00', 'ACTIVE'),
                       (887002, 888001, 889001, '2026-08-23 10:30:00', 'ACTIVE'),
                       (887003, 888001, 889001, '2026-08-23 11:00:00', 'ACTIVE')
                """);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM booking_slot WHERE id BETWEEN 887001 AND 887003");
    }

    @Test
    void removesTheTenOClockSlotButKeepsTheCurrentTenThirtySlot() {
        assertThat(slotCleanupService.cleanupPastSlots()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_slot WHERE id = 887001", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_slot WHERE id IN (887002, 887003)", Integer.class)).isEqualTo(2);
    }

    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(Instant.parse("2026-08-23T02:30:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
