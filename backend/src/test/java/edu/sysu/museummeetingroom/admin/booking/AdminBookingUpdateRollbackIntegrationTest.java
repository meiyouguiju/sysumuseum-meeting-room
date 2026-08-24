package edu.sysu.museummeetingroom.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import edu.sysu.museummeetingroom.admin.booking.command.AdminUpdateBookingCommand;
import edu.sysu.museummeetingroom.admin.booking.service.AdminBookingUpdateService;
import edu.sysu.museummeetingroom.booking.mapper.BookingAuditLogMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = "local.current-user-id=986501")
@Import(AdminBookingUpdateRollbackIntegrationTest.FixedClockConfiguration.class)
class AdminBookingUpdateRollbackIntegrationTest {

    private final AdminBookingUpdateService adminBookingUpdateService;
    private final JdbcTemplate jdbcTemplate;

    @SpyBean
    private BookingAuditLogMapper bookingAuditLogMapper;

    @Autowired
    AdminBookingUpdateRollbackIntegrationTest(AdminBookingUpdateService adminBookingUpdateService, JdbcTemplate jdbcTemplate) {
        this.adminBookingUpdateService = adminBookingUpdateService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        clean();
        jdbcTemplate.update("INSERT INTO sys_user(id,auth_provider,external_subject,login_name,display_name,role_code,status) VALUES (986501,'TEST','rollback-admin','rollback-admin','回滚管理员','ADMIN','ACTIVE')");
        jdbcTemplate.update("INSERT INTO meeting_room(id,name,location,capacity,status,sort_order) VALUES (986510,'回滚A','测试',20,'ENABLED',1),(986511,'回滚B','测试',20,'ENABLED',2)");
        jdbcTemplate.update("INSERT INTO booking(id,booking_no,room_id,organizer_user_id,organizer_name_snapshot,subject,attendee_count,participants_text,description,start_time,end_time,status,version,last_modified_at,last_modified_by_user_id) VALUES (986512,'D2C-ROLLBACK',986510,986501,'回滚管理员','原主题',3,'原参会人','原说明','2026-08-22 11:00:00','2026-08-22 12:00:00','ACTIVE',1,'2026-08-20 09:00:00',986501)");
        jdbcTemplate.update("INSERT INTO booking_slot(booking_id,room_id,slot_start,occupancy_state) VALUES (986512,986510,'2026-08-22 11:00:00','ACTIVE'),(986512,986510,'2026-08-22 11:30:00','ACTIVE')");
    }

    @AfterEach
    void tearDown() {
        reset(bookingAuditLogMapper);
        clean();
    }

    @Test
    void rollsBackScheduleMutationWhenAdminUpdateAuditWriteFails() {
        doThrow(new RuntimeException("审计故障注入")).when(bookingAuditLogMapper).insertAdminUpdateAudit(any());
        assertThatThrownBy(() -> adminBookingUpdateService.update(986512, new AdminUpdateBookingCommand(1, 986511L, "新主题",
                LocalDateTime.of(2026, 8, 22, 13, 0), LocalDateTime.of(2026, 8, 22, 14, 0), 8, "新参会人", "新说明", null)))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM booking WHERE id=986512", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM booking WHERE id=986512", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT room_id FROM booking WHERE id=986512", Long.class)).isEqualTo(986510L);
        assertThat(jdbcTemplate.queryForObject("SELECT subject FROM booking WHERE id=986512", String.class)).isEqualTo("原主题");
        assertThat(jdbcTemplate.queryForObject("SELECT attendee_count FROM booking WHERE id=986512", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT participants_text FROM booking WHERE id=986512", String.class)).isEqualTo("原参会人");
        assertThat(jdbcTemplate.queryForObject("SELECT description FROM booking WHERE id=986512", String.class)).isEqualTo("原说明");
        assertThat(jdbcTemplate.queryForObject("SELECT last_modified_at FROM booking WHERE id=986512", LocalDateTime.class)).isEqualTo(LocalDateTime.of(2026, 8, 20, 9, 0));
        assertThat(jdbcTemplate.queryForObject("SELECT last_modified_by_user_id FROM booking WHERE id=986512", Long.class)).isEqualTo(986501L);
        assertThat(slots()).containsExactly("986510:2026-08-22T11:00", "986510:2026-08-22T11:30");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id=986512", Integer.class)).isZero();
    }

    private List<String> slots() {
        return jdbcTemplate.queryForList("SELECT CONCAT(room_id, ':', DATE_FORMAT(slot_start, '%Y-%m-%dT%H:%i')) FROM booking_slot WHERE booking_id=986512 ORDER BY slot_start", String.class);
    }

    private void clean() {
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE booking_id=986512");
        jdbcTemplate.update("DELETE FROM booking_slot WHERE booking_id=986512");
        jdbcTemplate.update("DELETE FROM booking WHERE id=986512");
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id IN (986510,986511)");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id=986501");
    }

    static class FixedClockConfiguration {
        @Bean @Primary Clock fixedBusinessClock() { return Clock.fixed(Instant.parse("2026-08-22T02:12:00Z"), ZoneId.of("Asia/Shanghai")); }
    }
}
