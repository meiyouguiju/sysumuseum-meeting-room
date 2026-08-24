package edu.sysu.museummeetingroom.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "local.current-user-id=986201")
class AdminBookingUpdateForbiddenIntegrationTest {

    private static final long USER_ID = 986201L;
    private static final long ROOM_ID = 986210L;
    private static final long BOOKING_ID = 986211L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AdminBookingUpdateForbiddenIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        removeFixture();
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, role_code, status)
                VALUES (986201, 'TEST', 'admin-update-user', 'admin-update-user', '普通用户', 'USER', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO meeting_room(id, name, location, capacity, status, sort_order)
                VALUES (986210, '权限测试会议室', '测试地点', 20, 'ENABLED', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO booking(id, booking_no, room_id, organizer_user_id, organizer_name_snapshot, subject,
                    start_time, end_time, status, version)
                VALUES (986211, 'ADMIN-UPDATE-FORBIDDEN', 986210, 986201, '普通用户', '原主题',
                    '2026-08-22 11:00:00', '2026-08-22 12:00:00', 'ACTIVE', 1)
                """);
        jdbcTemplate.update("INSERT INTO booking_slot(booking_id, room_id, slot_start, occupancy_state) VALUES (986211, 986210, '2026-08-22 11:00:00', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO booking_slot(booking_id, room_id, slot_start, occupancy_state) VALUES (986211, 986210, '2026-08-22 11:30:00', 'ACTIVE')");
    }

    @AfterEach
    void tearDown() {
        removeFixture();
    }

    @Test
    void userCannotUpdateOwnBookingThroughAdminEndpoint() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/bookings/{bookingId}", BOOKING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":1,"roomId":986210,"subject":"不应更新","startTime":"2026-08-22T11:00:00",
                                "endTime":"2026-08-22T12:00:00","attendeeCount":1}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        assertThat(jdbcTemplate.queryForObject("SELECT subject FROM booking WHERE id = 986211", String.class)).isEqualTo("原主题");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM booking WHERE id = 986211", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id = 986211", Integer.class)).isZero();
    }

    private void removeFixture() {
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE booking_id = ?", BOOKING_ID);
        jdbcTemplate.update("DELETE FROM booking_slot WHERE booking_id = ?", BOOKING_ID);
        jdbcTemplate.update("DELETE FROM booking WHERE id = ?", BOOKING_ID);
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id = ?", ROOM_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", USER_ID);
    }
}
