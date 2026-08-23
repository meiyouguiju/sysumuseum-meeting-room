package edu.sysu.museummeetingroom.booking;

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
@TestPropertySource(properties = "local.current-user-id=970001")
class BookingDetailAdminIntegrationTest {

    private static final long ADMIN_ID = 970001L;
    private static final long OWNER_ID = 970002L;
    private static final long ROOM_ID = 970003L;
    private static final long BOOKING_ID = 970004L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    BookingDetailAdminIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        removeFixture();
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, role_code, status)
                VALUES (970001, 'TEST', 'detail-admin', 'detail-admin', '详情管理员', 'ADMIN', 'ACTIVE'),
                       (970002, 'TEST', 'detail-owner', 'detail-owner', '详情预约人', 'USER', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO meeting_room(id, name, location, capacity, status, sort_order)
                VALUES (970003, '详情管理员测试会议室', '测试地点', 20, 'ENABLED', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO booking(id, booking_no, room_id, organizer_user_id, organizer_name_snapshot, subject,
                    participants_text, description, start_time, end_time, status, version)
                VALUES (970004, 'TEST-970004', 970003, 970002, '详情预约人', '管理员读取他人预约',
                    '管理员可见参会人员', '管理员可见说明',
                    '2026-08-22 11:00:00', '2026-08-22 12:00:00', 'ACTIVE', 3)
                """);
    }

    @AfterEach
    void tearDown() {
        removeFixture();
    }

    @Test
    void adminCanReadAnotherUsersFullBookingDetail() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/{bookingId}", BOOKING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizer.id").value(OWNER_ID))
                .andExpect(jsonPath("$.participantsText").value("管理员可见参会人员"))
                .andExpect(jsonPath("$.description").value("管理员可见说明"))
                .andExpect(jsonPath("$.version").value(3));
    }

    private void removeFixture() {
        jdbcTemplate.update("DELETE FROM booking_slot WHERE room_id = ?", ROOM_ID);
        jdbcTemplate.update("DELETE FROM booking WHERE room_id = ?", ROOM_ID);
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id = ?", ROOM_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", ADMIN_ID, OWNER_ID);
    }
}
