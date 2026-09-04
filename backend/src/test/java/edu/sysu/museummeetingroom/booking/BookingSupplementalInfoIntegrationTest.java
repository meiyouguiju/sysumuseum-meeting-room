package edu.sysu.museummeetingroom.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "local.current-user-id=989001")
@Import(BookingSupplementalInfoIntegrationTest.FixedClockConfiguration.class)
class BookingSupplementalInfoIntegrationTest {

    private static final long OWNER_ID = 989001L;
    private static final long OTHER_USER_ID = 989002L;
    private static final long ROOM_ID = 989003L;
    private static final long UPCOMING_ID = 989101L;
    private static final long IN_PROGRESS_ID = 989102L;
    private static final long ENDED_ID = 989103L;
    private static final long CANCELLED_ID = 989104L;
    private static final long OTHER_BOOKING_ID = 989105L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    BookingSupplementalInfoIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        removeFixtures();
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, role_code, status)
                VALUES (989001, 'TEST', 'supplement-owner', 'supplement-owner', '补充信息预约人', 'USER', 'ACTIVE'),
                       (989002, 'TEST', 'supplement-other', 'supplement-other', '补充信息其他人', 'USER', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO meeting_room(id, name, location, capacity, status, sort_order)
                VALUES (989003, '补充信息测试会议室', '测试地点', 100, 'ENABLED', 1)
                """);
        insertBooking(UPCOMING_ID, OWNER_ID, "未开始", "2026-08-22 11:00:00", "2026-08-22 12:00:00", "ACTIVE");
        insertBooking(IN_PROGRESS_ID, OWNER_ID, "进行中", "2026-08-22 10:00:00", "2026-08-22 11:00:00", "ACTIVE");
        insertBooking(ENDED_ID, OWNER_ID, "已结束", "2026-08-22 09:00:00", "2026-08-22 10:00:00", "ACTIVE");
        insertBooking(CANCELLED_ID, OWNER_ID, "已取消", "2026-08-22 13:00:00", "2026-08-22 14:00:00", "CANCELLED");
        insertBooking(OTHER_BOOKING_ID, OTHER_USER_ID, "他人预约", "2026-08-22 14:00:00", "2026-08-22 15:00:00", "ACTIVE");
        insertSlots(UPCOMING_ID, "2026-08-22 11:00:00", "2026-08-22 11:30:00");
        insertSlots(IN_PROGRESS_ID, "2026-08-22 10:00:00", "2026-08-22 10:30:00");
        insertSlots(ENDED_ID, "2026-08-22 09:00:00", "2026-08-22 09:30:00");
        insertSlots(OTHER_BOOKING_ID, "2026-08-22 14:00:00", "2026-08-22 14:30:00");
    }

    @AfterEach
    void tearDown() {
        removeFixtures();
    }

    @Test
    void ownerCanSupplementUpcomingInProgressEndedAndCancelledBookingsWithoutChangingSlots() throws Exception {
        for (long bookingId : List.of(UPCOMING_ID, IN_PROGRESS_ID, ENDED_ID, CANCELLED_ID)) {
            mockMvc.perform(request(bookingId, 1, 12, "  张三\n李四  ", "  会后备注  "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(bookingId))
                    .andExpect(jsonPath("$.attendeeCount").value(12))
                    .andExpect(jsonPath("$.participantsText").value("张三\n李四"))
                    .andExpect(jsonPath("$.description").value("会后备注"))
                    .andExpect(jsonPath("$.version").value(2));
        }

        assertThat(slotStarts(UPCOMING_ID)).containsExactly("2026-08-22T11:00", "2026-08-22T11:30");
        assertThat(slotStarts(IN_PROGRESS_ID)).containsExactly("2026-08-22T10:00", "2026-08-22T10:30");
        assertThat(slotStarts(ENDED_ID)).containsExactly("2026-08-22T09:00", "2026-08-22T09:30");
        assertSupplementalAudit(ENDED_ID, OWNER_ID, "USER", OWNER_ID);
        assertThat(value("SELECT last_modified_by_user_id FROM booking WHERE id = ?", Long.class, CANCELLED_ID))
                .isEqualTo(OWNER_ID);
    }

    @Test
    void normalUserCannotSupplementAnotherUsersBooking() throws Exception {
        mockMvc.perform(request(OTHER_BOOKING_ID, 1, 8, "人员", "说明"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_ACCESS_DENIED"));
        assertThat(value("SELECT version FROM booking WHERE id = ?", Integer.class, OTHER_BOOKING_ID)).isEqualTo(1);
    }

    @Test
    void activeAdminCanSupplementOwnAndOtherBookingsWithoutReason() throws Exception {
        jdbcTemplate.update("UPDATE sys_user SET role_code = 'ADMIN' WHERE id = ?", OWNER_ID);

        mockMvc.perform(request(UPCOMING_ID, 1, 9, null, "管理员本人补充"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(request(OTHER_BOOKING_ID, 1, 10, "其他预约人", "管理员补充"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        assertSupplementalAudit(OTHER_BOOKING_ID, OWNER_ID, "ADMIN", OTHER_USER_ID);
    }

    @Test
    void normalizesBlankTextAllowsNullableCountAndRejectsStaleVersionMissingBookingAndUnexpectedFields() throws Exception {
        mockMvc.perform(request(UPCOMING_ID, 1, null, "  ", "\n"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendeeCount").doesNotExist())
                .andExpect(jsonPath("$.participantsText").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist());
        assertThat(value("SELECT attendee_count FROM booking WHERE id = ?", Integer.class, UPCOMING_ID)).isNull();
        assertThat(value("SELECT participants_text FROM booking WHERE id = ?", String.class, UPCOMING_ID)).isNull();
        assertThat(value("SELECT description FROM booking WHERE id = ?", String.class, UPCOMING_ID)).isNull();

        mockMvc.perform(request(UPCOMING_ID, 1, 1, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_VERSION_CONFLICT"));
        mockMvc.perform(request(123456789L, 1, 1, null, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/bookings/{bookingId}/supplemental-info", UPCOMING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2,\"subject\":\"不得修改\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_BODY_INVALID"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            long bookingId,
            int version,
            Integer attendeeCount,
            String participantsText,
            String description) {
        return patch("/api/v1/bookings/{bookingId}/supplemental-info", bookingId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version":%d,"attendeeCount":%s,"participantsText":%s,"description":%s}
                        """.formatted(version, number(attendeeCount), json(participantsText), json(description)));
    }

    private void assertSupplementalAudit(long bookingId, long actorUserId, String actorRole, long targetOwnerUserId) {
        assertThat(value("SELECT operation_type FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId))
                .isEqualTo("UPDATE");
        assertThat(value("SELECT actor_user_id FROM booking_audit_log WHERE booking_id = ?", Long.class, bookingId))
                .isEqualTo(actorUserId);
        assertThat(value("SELECT actor_role_snapshot FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId))
                .isEqualTo(actorRole);
        assertThat(value("SELECT target_owner_user_id FROM booking_audit_log WHERE booking_id = ?", Long.class, bookingId))
                .isEqualTo(targetOwnerUserId);
        assertThat(value("SELECT reason FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isNull();
        assertThat(value("SELECT slot_change_json FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isNull();
        assertThat(value("SELECT JSON_UNQUOTE(before_json->'$.subject') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId))
                .isEqualTo(value("SELECT subject FROM booking WHERE id = ?", String.class, bookingId));
        assertThat(value("SELECT JSON_UNQUOTE(after_json->'$.version') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId))
                .isEqualTo("2");
    }

    private void insertBooking(long id, long organizerUserId, String subject, String start, String end, String status) {
        String organizerName = organizerUserId == OWNER_ID ? "补充信息预约人" : "补充信息其他人";
        jdbcTemplate.update("""
                INSERT INTO booking(id, booking_no, room_id, organizer_user_id, organizer_name_snapshot, subject,
                    attendee_count, participants_text, description, start_time, end_time, status, version)
                VALUES (?, ?, ?, ?, ?, ?, 1, '原参会人', '原说明', ?, ?, ?, 1)
                """, id, "SUPPLEMENT-" + id, ROOM_ID, organizerUserId, organizerName, subject, start, end, status);
    }

    private void insertSlots(long bookingId, String... slotStarts) {
        for (String slotStart : slotStarts) {
            jdbcTemplate.update("""
                    INSERT INTO booking_slot(booking_id, room_id, slot_start, occupancy_state)
                    VALUES (?, ?, ?, 'ACTIVE')
                    """, bookingId, ROOM_ID, slotStart);
        }
    }

    private List<String> slotStarts(long bookingId) {
        return jdbcTemplate.queryForList(
                        "SELECT slot_start FROM booking_slot WHERE booking_id = ? ORDER BY slot_start",
                        java.time.LocalDateTime.class,
                        bookingId)
                .stream()
                .map(Object::toString)
                .toList();
    }

    private <T> T value(String sql, Class<T> type, long bookingId) {
        return jdbcTemplate.queryForObject(sql, type, bookingId);
    }

    private String number(Number value) {
        return value == null ? "null" : value.toString();
    }

    private String json(String value) {
        return value == null ? "null" : '"' + value.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"") + '"';
    }

    private void removeFixtures() {
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE booking_id BETWEEN 989101 AND 989105");
        jdbcTemplate.update("DELETE FROM booking_slot WHERE booking_id BETWEEN 989101 AND 989105");
        jdbcTemplate.update("DELETE FROM booking WHERE id BETWEEN 989101 AND 989105");
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id = ?", ROOM_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", OWNER_ID, OTHER_USER_ID);
    }

    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(Instant.parse("2026-08-22T02:12:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
