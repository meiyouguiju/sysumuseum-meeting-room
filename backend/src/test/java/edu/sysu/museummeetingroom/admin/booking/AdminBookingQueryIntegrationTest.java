package edu.sysu.museummeetingroom.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "local.current-user-id=984001")
@Import(AdminBookingQueryIntegrationTest.FixedClockConfiguration.class)
class AdminBookingQueryIntegrationTest {

    private static final long ADMIN_ID = 984001L;
    private static final long OWNER_ID = 984002L;
    private static final long ROOM_ID = 984010L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private long bookingsBeforeFixture;

    @Autowired
    AdminBookingQueryIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        removeFixture();
        bookingsBeforeFixture = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking", Long.class);
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, role_code, status)
                VALUES (984001, 'TEST', 'admin-list-admin', 'admin-list-admin', '列表管理员', 'ADMIN', 'ACTIVE'),
                       (984002, 'TEST', 'admin-list-owner', 'admin-list-owner', '列表预约人', 'USER', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO meeting_room(id, name, location, capacity, status, sort_order)
                VALUES (984010, '管理员列表测试会议室', '测试地点', 20, 'ENABLED', 1)
                """);
        insertBooking(984101L, "未来预约A", "2026-08-22 11:00:00", "2026-08-22 12:00:00", "ACTIVE");
        insertBooking(984102L, "未来预约B", "2026-08-22 11:00:00", "2026-08-22 12:00:00", "ACTIVE");
        insertBooking(984103L, "进行中预约", "2026-08-22 10:00:00", "2026-08-22 10:30:00", "ACTIVE");
        insertBooking(984104L, "已结束预约", "2026-08-22 09:00:00", "2026-08-22 10:00:00", "ACTIVE");
        insertBooking(984105L, "已取消预约", "2026-08-22 12:00:00", "2026-08-22 12:30:00", "CANCELLED");
    }

    @AfterEach
    void tearDown() {
        removeFixture();
    }

    @Test
    void adminListsAllStatusesWithDefaultPaginationAndPrivacySafeSummaryFields() throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(bookingsBeforeFixture + 5))
                .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(20)));
        assertFixtureSummary(984101L, "UPCOMING");
        assertFixtureSummary(984103L, "IN_PROGRESS");
        assertFixtureSummary(984104L, "ENDED");
        assertFixtureSummary(984105L, "CANCELLED");
    }

    @Test
    void listsUseFixedStartTimeAndIdDescendingOrderAndCorrectPageSize() throws Exception {
        assertThat(positionOf(984102L)).isLessThan(positionOf(984101L));
        mockMvc.perform(get("/api/v1/admin/bookings").param("page", String.valueOf(positionOf(984102L) + 1)).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(984102));
        mockMvc.perform(get("/api/v1/admin/bookings").param("page", String.valueOf(positionOf(984101L) + 1)).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(984101));
    }

    @Test
    void rejectsInvalidPaginationWithoutSilentTruncation() throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/admin/bookings").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/admin/bookings").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
    }

    @Test
    void acceptsLargePageWithoutOffsetOverflow() throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings")
                        .param("page", String.valueOf(Integer.MAX_VALUE))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(Integer.MAX_VALUE))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.total").value(bookingsBeforeFixture + 5))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void filtersByOrganizerDateAndDerivedStatusBeforePagination() throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings")
                        .param("organizerKeyword", " 列表预约人 ")
                        .param("date", "2026-08-22")
                        .param("status", "UPCOMING")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].id").value(984102));
        mockMvc.perform(get("/api/v1/admin/bookings").param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == 984103)].displayStatus").value("IN_PROGRESS"));
        mockMvc.perform(get("/api/v1/admin/bookings").param("status", "ACTIVE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
    }

    @Test
    void scheduleMarksOnlyTheAdministratorsOwnBookingAsMine() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO booking(id, booking_no, room_id, organizer_user_id, organizer_name_snapshot, subject,
                    start_time, end_time, status, version)
                VALUES (984106, 'ADMIN-LIST-984106', ?, ?, '列表管理员', '管理员本人预约',
                    '2026-08-22 13:00:00', '2026-08-22 13:30:00', 'ACTIVE', 1)
                """, ROOM_ID, ADMIN_ID);

        mockMvc.perform(get("/api/v1/schedules").param("date", "2026-08-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings[?(@.id == 984106)].isMine").value(true))
                .andExpect(jsonPath("$.bookings[?(@.id == 984101)].isMine").value(false));
    }

    private void assertFixtureSummary(long bookingId, String displayStatus) throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings")
                        .param("page", String.valueOf(positionOf(bookingId) + 1))
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(bookingId))
                .andExpect(jsonPath("$.items[0].organizerUserId").value(OWNER_ID))
                .andExpect(jsonPath("$.items[0].organizerName").value("列表预约人"))
                .andExpect(jsonPath("$.items[0].status").exists())
                .andExpect(jsonPath("$.items[0].displayStatus").value(displayStatus))
                .andExpect(jsonPath("$.items[0].participantsText").doesNotExist())
                .andExpect(jsonPath("$.items[0].description").doesNotExist());
    }

    private long positionOf(long bookingId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM booking candidate
                INNER JOIN booking fixture ON fixture.id = ?
                WHERE candidate.start_time > fixture.start_time
                   OR (candidate.start_time = fixture.start_time AND candidate.id > fixture.id)
                """, Long.class, bookingId);
    }

    private void insertBooking(long id, String subject, String startTime, String endTime, String status) {
        jdbcTemplate.update("""
                INSERT INTO booking(id, booking_no, room_id, organizer_user_id, organizer_name_snapshot, subject,
                    participants_text, description, start_time, end_time, status, version)
                VALUES (?, ?, ?, ?, '列表预约人', ?, '不应出现在列表的参会人员', '不应出现在列表的说明', ?, ?, ?, 1)
                """, id, "ADMIN-LIST-" + id, ROOM_ID, OWNER_ID, subject, startTime, endTime, status);
    }

    private void removeFixture() {
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE booking_id BETWEEN 984101 AND 984106");
        jdbcTemplate.update("DELETE FROM booking_slot WHERE booking_id BETWEEN 984101 AND 984106");
        jdbcTemplate.update("DELETE FROM booking WHERE id BETWEEN 984101 AND 984106");
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id = ?", ROOM_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", ADMIN_ID, OWNER_ID);
    }

    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(Instant.parse("2026-08-22T02:15:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
