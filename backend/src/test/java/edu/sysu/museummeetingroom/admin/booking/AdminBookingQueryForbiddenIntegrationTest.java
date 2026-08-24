package edu.sysu.museummeetingroom.admin.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "local.current-user-id=984201")
class AdminBookingQueryForbiddenIntegrationTest {

    private static final long USER_ID = 984201L;
    private static final long OWNER_ID = 984202L;
    private static final long ROOM_ID = 984210L;
    private static final long BOOKING_ID = 984211L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AdminBookingQueryForbiddenIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        removeFixture();
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, role_code, status)
                VALUES (984201, 'TEST', 'admin-list-user', 'admin-list-user', '普通用户', 'USER', 'ACTIVE'),
                       (984202, 'TEST', 'admin-list-other', 'admin-list-other', '他人预约人', 'USER', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO meeting_room(id, name, location, capacity, status, sort_order)
                VALUES (984210, '管理员列表拒绝测试室', '测试地点', 20, 'ENABLED', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO booking(id, booking_no, room_id, organizer_user_id, organizer_name_snapshot, subject,
                    start_time, end_time, status, version)
                VALUES (984211, 'ADMIN-LIST-984211', 984210, 984202, '他人预约人', '他人预约',
                    '2026-08-22 11:00:00', '2026-08-22 12:00:00', 'ACTIVE', 1)
                """);
    }

    @AfterEach
    void tearDown() {
        removeFixture();
    }

    @Test
    void userCannotReadAdminListOrAnotherUsersBookingDetail() throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/bookings/{bookingId}", BOOKING_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_ACCESS_DENIED"));
    }

    private void removeFixture() {
        jdbcTemplate.update("DELETE FROM booking_slot WHERE booking_id = ?", BOOKING_ID);
        jdbcTemplate.update("DELETE FROM booking WHERE id = ?", BOOKING_ID);
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id = ?", ROOM_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", USER_ID, OWNER_ID);
    }
}
