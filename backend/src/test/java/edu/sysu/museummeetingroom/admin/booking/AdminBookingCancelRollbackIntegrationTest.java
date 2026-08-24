package edu.sysu.museummeetingroom.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import edu.sysu.museummeetingroom.admin.booking.service.AdminBookingCancelService;
import edu.sysu.museummeetingroom.booking.mapper.BookingAuditLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = "local.current-user-id=987201")
class AdminBookingCancelRollbackIntegrationTest {
    @Autowired private AdminBookingCancelService service;
    @Autowired private JdbcTemplate jdbc;
    @SpyBean private BookingAuditLogMapper auditMapper;
    @BeforeEach void setup() {
        clean();
        jdbc.update("INSERT INTO sys_user(id,auth_provider,external_subject,login_name,display_name,role_code,status) VALUES (987201,'T','a','a','A','ADMIN','ACTIVE')");
        jdbc.update("INSERT INTO meeting_room(id,name,location,capacity,status,sort_order) VALUES (987210,'R','L',10,'ENABLED',1)");
        jdbc.update("INSERT INTO booking(id,booking_no,room_id,organizer_user_id,organizer_name_snapshot,subject,start_time,end_time,status,version) VALUES (987211,'R',987210,987201,'A','S','2026-08-22 11:00:00','2026-08-22 12:00:00','ACTIVE',1)");
        jdbc.update("INSERT INTO booking_slot(booking_id,room_id,slot_start,occupancy_state) VALUES (987211,987210,'2026-08-22 11:00:00','ACTIVE'),(987211,987210,'2026-08-22 11:30:00','ACTIVE')");
    }
    @AfterEach void cleanup() {
        reset(auditMapper);
        clean();
    }
    @Test void rollsBackWhenAdminAuditFails() {
        doThrow(new RuntimeException("fault")).when(auditMapper).insertAdminCancelAudit(any());
        assertThatThrownBy(() -> service.cancel(987211, 1, null)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM booking WHERE id=987211", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT version FROM booking WHERE id=987211", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_slot WHERE booking_id=987211", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id=987211", Integer.class)).isZero();
    }
    private void clean() {
        jdbc.update("DELETE FROM booking_audit_log WHERE booking_id=987211");
        jdbc.update("DELETE FROM booking_slot WHERE booking_id=987211");
        jdbc.update("DELETE FROM booking WHERE id=987211");
        jdbc.update("DELETE FROM meeting_room WHERE id=987210");
        jdbc.update("DELETE FROM sys_user WHERE id=987201");
    }
}
