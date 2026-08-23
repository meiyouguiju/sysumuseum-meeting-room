package edu.sysu.museummeetingroom.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "local.current-user-id=980001")
@Import(BookingUpdateIntegrationTest.FixedClockConfiguration.class)
class BookingUpdateIntegrationTest {

    private static final long USER_ID = 980001L;
    private static final long OTHER_USER_ID = 980002L;
    private static final long ROOM_A = 980003L;
    private static final long ROOM_B = 980004L;
    private static final long DISABLED_ROOM = 980005L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    BookingUpdateIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        removeFixture();
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, role_code, status)
                VALUES (980001, 'TEST', 'update-owner', 'update-owner', '修改预约人', 'USER', 'ACTIVE'),
                       (980002, 'TEST', 'update-other', 'update-other', '其他预约人', 'USER', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO meeting_room(id, name, location, capacity, status, sort_order)
                VALUES (980003, '修改测试A', '测试地点', 10, 'ENABLED', 1),
                       (980004, '修改测试B', '测试地点', 10, 'ENABLED', 2),
                       (980005, '修改测试停用', '测试地点', 10, 'DISABLED', 3)
                """);
        insertBooking(980101, ROOM_A, USER_ID, "原主题", "2026-08-22 11:00:00", "2026-08-22 12:00:00", "ACTIVE", 1);
        insertSlots(980101, ROOM_A, "2026-08-22 11:00:00", "2026-08-22 11:30:00");
        insertBooking(980102, ROOM_B, OTHER_USER_ID, "占用", "2026-08-22 14:00:00", "2026-08-22 15:00:00", "ACTIVE", 1);
        insertSlots(980102, ROOM_B, "2026-08-22 14:00:00", "2026-08-22 14:30:00");
        insertBooking(980103, ROOM_A, OTHER_USER_ID, "他人预约", "2026-08-22 13:00:00", "2026-08-22 13:30:00", "ACTIVE", 1);
        insertBooking(980104, ROOM_A, USER_ID, "已取消", "2026-08-22 13:00:00", "2026-08-22 13:30:00", "CANCELLED", 1);
        insertBooking(980105, ROOM_A, USER_ID, "已开始", "2026-08-22 10:00:00", "2026-08-22 10:30:00", "ACTIVE", 1);
        insertBooking(980107, ROOM_A, USER_ID, "已结束", "2026-08-22 09:00:00", "2026-08-22 09:30:00", "ACTIVE", 1);
        insertBooking(980108, ROOM_A, USER_ID, "窗口外", "2026-09-10 11:00:00", "2026-09-10 12:00:00", "ACTIVE", 1);
        insertSlots(980108, ROOM_A, "2026-09-10 11:00:00", "2026-09-10 11:30:00");
        insertBooking(980109, ROOM_A, USER_ID, "开始边界", "2026-08-22 10:15:00", "2026-08-22 10:45:00", "ACTIVE", 1);
        insertBooking(980106, DISABLED_ROOM, USER_ID, "停用旧房间", "2026-08-22 15:00:00", "2026-08-22 15:30:00", "ACTIVE", 1);
        insertSlots(980106, DISABLED_ROOM, "2026-08-22 15:00:00");
    }

    @AfterEach
    void tearDown() {
        removeFixture();
    }

    @Test
    void updatesNonScheduleFieldsWithoutChangingSlotsAndWritesAudit() throws Exception {
        mockMvc.perform(patchRequest(980101, body(1, ROOM_A, "  新主题  ", "11:00:00", "12:00:00", 12, " 张三\n李四 ", "  新说明  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("新主题"))
                .andExpect(jsonPath("$.participantsText").value("张三\n李四"))
                .andExpect(jsonPath("$.description").value("新说明"))
                .andExpect(jsonPath("$.version").value(2));

        assertThat(slotStarts(980101)).containsExactly("2026-08-22T11:00", "2026-08-22T11:30");
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(before_json->'$.subject') FROM booking_audit_log WHERE booking_id = 980101", String.class))
                .isEqualTo("原主题");
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(after_json->'$.subject') FROM booking_audit_log WHERE booking_id = 980101", String.class))
                .isEqualTo("新主题");
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_EXTRACT(slot_change_json, '$.scheduleChanged') FROM booking_audit_log WHERE booking_id = 980101", String.class))
                .isEqualTo("false");
    }

    @Test
    void updatesScheduleAndRollsBackWhenNewSlotsConflict() throws Exception {
        mockMvc.perform(patchRequest(980101, body(1, ROOM_B, "重叠改期", "12:30:00", "13:30:00", 1, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.startTime").value("2026-08-22T12:30:00"));
        assertThat(slotStarts(980101)).containsExactly("2026-08-22T12:30", "2026-08-22T13:00");
        assertThat(jdbcTemplate.queryForObject("SELECT operation_type FROM booking_audit_log WHERE booking_id=980101", String.class)).isEqualTo("UPDATE");
        assertThat(jdbcTemplate.queryForObject("SELECT version_before FROM booking_audit_log WHERE booking_id=980101", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT version_after FROM booking_audit_log WHERE booking_id=980101", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(before_json->'$.roomId') FROM booking_audit_log WHERE booking_id=980101", String.class)).isEqualTo(String.valueOf(ROOM_A));
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(after_json->'$.roomId') FROM booking_audit_log WHERE booking_id=980101", String.class)).isEqualTo(String.valueOf(ROOM_B));
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(before_json->'$.startTime') FROM booking_audit_log WHERE booking_id=980101", String.class)).isEqualTo("2026-08-22T11:00:00");
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(before_json->'$.endTime') FROM booking_audit_log WHERE booking_id=980101", String.class)).isEqualTo("2026-08-22T12:00:00");
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(before_json->'$.version') FROM booking_audit_log WHERE booking_id=980101", String.class)).isEqualTo("1");
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(after_json->'$.startTime') FROM booking_audit_log WHERE booking_id=980101", String.class)).isEqualTo("2026-08-22T12:30:00");
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(after_json->'$.endTime') FROM booking_audit_log WHERE booking_id=980101", String.class)).isEqualTo("2026-08-22T13:30:00");
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(after_json->'$.version') FROM booking_audit_log WHERE booking_id=980101", String.class)).isEqualTo("2");
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_EXTRACT(slot_change_json,'$.scheduleChanged') FROM booking_audit_log WHERE booking_id=980101", String.class)).isEqualTo("true");
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(slot_change_json->'$.oldRoomId') FROM booking_audit_log WHERE booking_id=980101", String.class)).isEqualTo(String.valueOf(ROOM_A));
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(slot_change_json->'$.newRoomId') FROM booking_audit_log WHERE booking_id=980101", String.class)).isEqualTo(String.valueOf(ROOM_B));
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(slot_change_json->'$.oldStartTime') FROM booking_audit_log WHERE booking_id=980101", String.class)).isEqualTo("2026-08-22T11:00:00");
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(slot_change_json->'$.newStartTime') FROM booking_audit_log WHERE booking_id=980101", String.class)).isEqualTo("2026-08-22T12:30:00");
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(slot_change_json->'$.oldEndTime') FROM booking_audit_log WHERE booking_id=980101", String.class)).isEqualTo("2026-08-22T12:00:00");
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_UNQUOTE(slot_change_json->'$.newEndTime') FROM booking_audit_log WHERE booking_id=980101", String.class)).isEqualTo("2026-08-22T13:30:00");

        String subjectBefore = jdbcTemplate.queryForObject("SELECT subject FROM booking WHERE id=980101", String.class);
        Long roomBefore = jdbcTemplate.queryForObject("SELECT room_id FROM booking WHERE id=980101", Long.class);
        String startBefore = jdbcTemplate.queryForObject("SELECT start_time FROM booking WHERE id=980101", String.class);
        String endBefore = jdbcTemplate.queryForObject("SELECT end_time FROM booking WHERE id=980101", String.class);
        Integer versionBefore = jdbcTemplate.queryForObject("SELECT version FROM booking WHERE id=980101", Integer.class);
        java.util.List<String> slotsBefore = slotStarts(980101);
        Integer auditBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id=980101", Integer.class);

        mockMvc.perform(patchRequest(980101, body(2, ROOM_B, "冲突请求不应持久化", "14:00:00", "15:00:00", 1, null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_SLOT_CONFLICT"));
        assertThat(jdbcTemplate.queryForObject("SELECT subject FROM booking WHERE id=980101", String.class)).isEqualTo(subjectBefore).isNotEqualTo("冲突请求不应持久化");
        assertThat(jdbcTemplate.queryForObject("SELECT room_id FROM booking WHERE id=980101", Long.class)).isEqualTo(roomBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT start_time FROM booking WHERE id=980101", String.class)).isEqualTo(startBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT end_time FROM booking WHERE id=980101", String.class)).isEqualTo(endBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM booking WHERE id=980101", Integer.class)).isEqualTo(versionBefore);
        assertThat(slotStarts(980101)).isEqualTo(slotsBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id=980101", Integer.class)).isEqualTo(auditBefore);
    }

    @Test
    void rejectsStaleVersionOtherOwnerAndImmutableBookings() throws Exception {
        mockMvc.perform(patchRequest(980101, body(99, ROOM_A, "过期", "11:00:00", "12:00:00", 1, null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_VERSION_CONFLICT"));
        mockMvc.perform(patchRequest(980103, body(1, ROOM_A, "越权", "13:00:00", "13:30:00", 1, null, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_ACCESS_DENIED"));
        mockMvc.perform(patchRequest(980104, body(1, ROOM_A, "取消", "13:00:00", "13:30:00", 1, null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_ALREADY_CANCELLED"));
        mockMvc.perform(patchRequest(980105, body(1, ROOM_A, "开始", "10:00:00", "10:30:00", 1, null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_ALREADY_STARTED"));
        mockMvc.perform(patchRequest(980107, body(1, ROOM_A, "结束", "09:00:00", "09:30:00", 1, null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_ALREADY_ENDED"));
    }

    @Test
    void permitsNonScheduleUpdateOnDisabledOldRoomButRejectsDisabledScheduleTarget() throws Exception {
        mockMvc.perform(patchRequest(980106, body(1, DISABLED_ROOM, "停用房间文字修改", "15:00:00", "15:30:00", 11, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(patchRequest(980106, body(2, DISABLED_ROOM, "停用房间改期", "16:00:00", "16:30:00", 1, null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEETING_ROOM_DISABLED"));
        java.util.List<String> slotsBefore = slotStarts(980101);
        mockMvc.perform(patchRequest(980101, body(1, DISABLED_ROOM, "不得变化", "16:00:00", "16:30:00", 1, null, null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("MEETING_ROOM_DISABLED"));
        assertThat(jdbcTemplate.queryForObject("SELECT subject FROM booking WHERE id=980101", String.class)).isEqualTo("原主题");
        assertThat(jdbcTemplate.queryForObject("SELECT room_id FROM booking WHERE id=980101", Long.class)).isEqualTo(ROOM_A);
        assertThat(jdbcTemplate.queryForObject("SELECT start_time FROM booking WHERE id=980101", String.class)).isEqualTo("2026-08-22 11:00:00");
        assertThat(jdbcTemplate.queryForObject("SELECT end_time FROM booking WHERE id=980101", String.class)).isEqualTo("2026-08-22 12:00:00");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM booking WHERE id=980101", Integer.class)).isEqualTo(1);
        assertThat(slotStarts(980101)).isEqualTo(slotsBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id=980101", Integer.class)).isZero();
    }

    @Test
    void rejectsInvalidHttpBodiesBeforeAnyMutation() throws Exception {
        mockMvc.perform(patchRequest(980101, """
                {"roomId":980003,"subject":"主题","startTime":"2026-08-22T11:00:00","endTime":"2026-08-22T12:00:00"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
        mockMvc.perform(patchRequest(980101, body(1, ROOM_A, " ", "11:00:00", "12:00:00", 1, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
        mockMvc.perform(patchRequest(980101, """
                {"version":1,"roomId":980003,"subject":"主题","startTime":"invalid","endTime":"2026-08-22T12:00:00","attendeeCount":1,"extra":true}
                """))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject("SELECT version FROM booking WHERE id = 980101", Integer.class)).isEqualTo(1);
        assertThat(slotStarts(980101)).containsExactly("2026-08-22T11:00", "2026-08-22T11:30");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id = 980101", Integer.class)).isZero();
    }

    @Test
    void appliesWindowBoundaryCapacityAndValidationRules() throws Exception {
        mockMvc.perform(patchRequest(980108, bodyOnDate(1, ROOM_A, "窗口外已修改", "2026-09-10", "11:00:00", "12:00:00", 11, " 人员 ", " 说明 ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.attendeeCount").value(11));
        mockMvc.perform(patchRequest(980108, bodyOnDate(2, ROOM_A, "不应写入", "2026-09-10", "11:30:00", "12:30:00", 1, null, null)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errorCode").value("BOOKING_WINDOW_EXCEEDED"));
        assertThat(jdbcTemplate.queryForObject("SELECT subject FROM booking WHERE id=980108", String.class)).isEqualTo("窗口外已修改");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM booking WHERE id=980108", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id=980108", Integer.class)).isEqualTo(1);
        mockMvc.perform(patchRequest(980109, body(1, ROOM_A, "边界", "10:15:00", "10:45:00", 1, null, null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("BOOKING_ALREADY_STARTED"));
        String originalSlots = slotStarts(980101).toString();
        for (String invalid : java.util.List.of(
                body(0, ROOM_A, "主题", "11:00:00", "12:00:00", 1, null, null),
                body(1, ROOM_A, "a".repeat(201), "11:00:00", "12:00:00", 1, null, null),
                body(1, ROOM_A, "主题", "11:00:00", "12:00:00", 1, "a".repeat(2001), null),
                body(1, ROOM_A, "主题", "11:00:00", "12:00:00", 1, null, "a".repeat(4001)),
                body(1, ROOM_A, "主题", "11:00:00", "12:00:00", -1, null, null))) {
            mockMvc.perform(patchRequest(980101, invalid)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM booking WHERE id=980101", Integer.class)).isEqualTo(1);
        assertThat(slotStarts(980101).toString()).isEqualTo(originalSlots);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id=980101", Integer.class)).isZero();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder patchRequest(long bookingId, String body) {
        return patch("/api/v1/bookings/{bookingId}", bookingId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String body(long version, long roomId, String subject, String start, String end, Integer attendeeCount, String participants, String description) {
        return bodyOnDate(version, roomId, subject, "2026-08-22", start, end, attendeeCount, participants, description);
    }

    private String bodyOnDate(long version, long roomId, String subject, String date, String start, String end, Integer attendeeCount, String participants, String description) {
        return """
                {"version":%d,"roomId":%d,"subject":"%s","startTime":"%sT%s","endTime":"%sT%s","attendeeCount":%s,"participantsText":%s,"description":%s}
                """.formatted(version, roomId, subject, date, start, date, end, attendeeCount, json(participants), json(description));
    }

    private String json(String value) {
        return value == null ? "null" : '"' + value.replace("\n", "\\n") + '"';
    }

    private java.util.List<String> slotStarts(long bookingId) {
        return jdbcTemplate.queryForList(
                        "SELECT slot_start FROM booking_slot WHERE booking_id = ? ORDER BY slot_start",
                        java.time.LocalDateTime.class,
                        bookingId)
                .stream()
                .map(Object::toString)
                .toList();
    }

    private void insertBooking(long id, long roomId, long organizerId, String subject, String start, String end, String status, int version) {
        jdbcTemplate.update("""
                INSERT INTO booking(id, booking_no, room_id, organizer_user_id, organizer_name_snapshot, subject,
                    start_time, end_time, status, version)
                VALUES (?, ?, ?, ?, '修改预约人', ?, ?, ?, ?, ?)
                """, id, "UPDATE-" + id, roomId, organizerId, subject, start, end, status, version);
    }

    private void insertSlots(long bookingId, long roomId, String... slotStarts) {
        for (String slotStart : slotStarts) {
            jdbcTemplate.update("INSERT INTO booking_slot(booking_id, room_id, slot_start, occupancy_state) VALUES (?, ?, ?, 'ACTIVE')",
                    bookingId, roomId, slotStart);
        }
    }

    private void removeFixture() {
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE booking_id BETWEEN 980101 AND 980109");
        jdbcTemplate.update("DELETE FROM booking_slot WHERE booking_id BETWEEN 980101 AND 980109");
        jdbcTemplate.update("DELETE FROM booking WHERE id BETWEEN 980101 AND 980109");
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id BETWEEN 980003 AND 980005");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", USER_ID, OTHER_USER_ID);
    }

    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(Instant.parse("2026-08-22T02:15:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
