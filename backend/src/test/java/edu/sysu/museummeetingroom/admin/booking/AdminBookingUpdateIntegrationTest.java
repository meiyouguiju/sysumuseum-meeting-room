package edu.sysu.museummeetingroom.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
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
@TestPropertySource(properties = "local.current-user-id=986001")
@Import(AdminBookingUpdateIntegrationTest.FixedClockConfiguration.class)
class AdminBookingUpdateIntegrationTest {

    private static final long ADMIN_ID = 986001L;
    private static final long OWNER_ID = 986002L;
    private static final long ROOM_A = 986010L;
    private static final long ROOM_B = 986011L;
    private static final long DISABLED_ROOM = 986012L;
    private static final long UPCOMING_ID = 986101L;
    private static final long IN_PROGRESS_ID = 986102L;
    private static final long CANCELLED_ID = 986103L;
    private static final long ENDED_ID = 986104L;
    private static final long OUTSIDE_WINDOW_ID = 986105L;
    private static final long ADMIN_OWN_ID = 986106L;
    private static final long DISABLED_OLD_ROOM_ID = 986107L;
    private static final long CONFLICT_ID = 986108L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AdminBookingUpdateIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        removeFixture();
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, role_code, status)
                VALUES (986001, 'TEST', 'admin-update', 'admin-update', '管理员', 'ADMIN', 'ACTIVE'),
                       (986002, 'TEST', 'owner-update', 'owner-update', '预约人', 'USER', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO meeting_room(id, name, location, capacity, status, sort_order)
                VALUES (986010, '管理员修改A', '测试地点', 20, 'ENABLED', 1),
                       (986011, '管理员修改B', '测试地点', 20, 'ENABLED', 2),
                       (986012, '管理员修改停用', '测试地点', 20, 'DISABLED', 3)
                """);
        insertBooking(UPCOMING_ID, ROOM_A, OWNER_ID, "原主题", "2026-08-22 11:00:00", "2026-08-22 12:00:00", "ACTIVE", 1);
        insertSlots(UPCOMING_ID, ROOM_A, "2026-08-22 11:00:00", "2026-08-22 11:30:00");
        insertBooking(IN_PROGRESS_ID, ROOM_B, OWNER_ID, "进行中原主题", "2026-08-22 10:00:00", "2026-08-22 12:00:00", "ACTIVE", 1);
        insertSlots(IN_PROGRESS_ID, ROOM_B, "2026-08-22 10:00:00", "2026-08-22 10:30:00", "2026-08-22 11:00:00", "2026-08-22 11:30:00");
        insertBooking(CANCELLED_ID, ROOM_A, OWNER_ID, "已取消", "2026-08-22 13:00:00", "2026-08-22 14:00:00", "CANCELLED", 1);
        insertBooking(ENDED_ID, ROOM_A, OWNER_ID, "已结束", "2026-08-22 09:00:00", "2026-08-22 10:00:00", "ACTIVE", 1);
        insertBooking(OUTSIDE_WINDOW_ID, ROOM_A, OWNER_ID, "窗口外", "2026-09-10 11:00:00", "2026-09-10 12:00:00", "ACTIVE", 1);
        insertSlots(OUTSIDE_WINDOW_ID, ROOM_A, "2026-09-10 11:00:00", "2026-09-10 11:30:00");
        insertBooking(ADMIN_OWN_ID, ROOM_A, ADMIN_ID, "管理员自己", "2026-08-22 15:00:00", "2026-08-22 16:00:00", "ACTIVE", 1);
        insertSlots(ADMIN_OWN_ID, ROOM_A, "2026-08-22 15:00:00", "2026-08-22 15:30:00");
        insertBooking(DISABLED_OLD_ROOM_ID, DISABLED_ROOM, OWNER_ID, "停用旧房间", "2026-08-22 16:00:00", "2026-08-22 17:00:00", "ACTIVE", 1);
        insertSlots(DISABLED_OLD_ROOM_ID, DISABLED_ROOM, "2026-08-22 16:00:00", "2026-08-22 16:30:00");
        insertBooking(CONFLICT_ID, ROOM_B, OWNER_ID, "占用", "2026-08-22 13:30:00", "2026-08-22 14:00:00", "ACTIVE", 1);
        insertSlots(CONFLICT_ID, ROOM_B, "2026-08-22 13:30:00");
    }

    @AfterEach
    void tearDown() {
        removeFixture();
    }

    @Test
    void adminUpdatesAnotherUsersUpcomingNonScheduleFieldsAndWritesCompleteAudit() throws Exception {
        mockMvc.perform(request(UPCOMING_ID, body(1, ROOM_A, "  新主题  ", "2026-08-22T11:00:00", "2026-08-22T12:00:00", 12,
                        " 张三\n李四 ", " 新说明 ", "  管理员调整会议安排  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizer.id").value(OWNER_ID))
                .andExpect(jsonPath("$.subject").value("新主题"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.displayStatus").value("UPCOMING"));

        assertThat(slotStarts(UPCOMING_ID)).containsExactly("2026-08-22T11:00", "2026-08-22T11:30");
        assertThat(value("SELECT last_modified_by_user_id FROM booking WHERE id = ?", Long.class, UPCOMING_ID)).isEqualTo(ADMIN_ID);
        assertThat(value("SELECT last_modified_at FROM booking WHERE id = ?", LocalDateTime.class, UPCOMING_ID))
                .isEqualTo(LocalDateTime.of(2026, 8, 22, 10, 12));
        assertCompleteAudit(UPCOMING_ID, ROOM_A, ROOM_A, "2026-08-22T11:00:00", "2026-08-22T12:00:00", false,
                "管理员调整会议安排", "原主题", "新主题", 1, 2);
    }

    @Test
    void requiresTrimmedReasonForAnotherUsersBookingAndAllowsMaximumLengthAndOwnUpdate() throws Exception {
        for (String reason : List.of("null", "\"\"", "\"   \"")) {
            mockMvc.perform(request(UPCOMING_ID, body(1, ROOM_A, "不应更新", "2026-08-22T11:00:00", "2026-08-22T12:00:00", 1,
                            null, null, reason)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
        }
        assertUnchanged(UPCOMING_ID, "原主题", 1, List.of("2026-08-22T11:00", "2026-08-22T11:30"));
        mockMvc.perform(request(UPCOMING_ID, body(1, ROOM_A, "500字符原因", "2026-08-22T11:00:00", "2026-08-22T12:00:00", 1,
                        null, null, "\"" + "a".repeat(500) + "\"")))
                .andExpect(status().isOk());
        assertThat(value("SELECT reason FROM booking_audit_log WHERE booking_id = ?", String.class, UPCOMING_ID)).hasSize(500);
        mockMvc.perform(request(ADMIN_OWN_ID, body(1, ROOM_A, "管理员自己更新", "2026-08-22T15:00:00", "2026-08-22T16:00:00", 1,
                        null, null, "null")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void changesUpcomingScheduleReplacesSlotsAndWritesScheduleAudit() throws Exception {
        mockMvc.perform(request(UPCOMING_ID, body(1, ROOM_B, "改期", "2026-08-22T12:30:00", "2026-08-22T13:30:00", 1,
                        "参会者", "说明", "调整会议室")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        assertThat(value("SELECT room_id FROM booking WHERE id = ?", Long.class, UPCOMING_ID)).isEqualTo(ROOM_B);
        assertThat(slotStarts(UPCOMING_ID)).containsExactly("2026-08-22T12:30", "2026-08-22T13:00");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_slot WHERE booking_id = ? AND room_id = ?", Integer.class,
                UPCOMING_ID, ROOM_A)).isZero();
        assertCompleteAudit(UPCOMING_ID, ROOM_A, ROOM_B, "2026-08-22T11:00:00", "2026-08-22T12:00:00", true,
                "调整会议室", "原主题", "改期", 1, 2);
    }

    @Test
    void rollsBackUpcomingScheduleWhenSlotConflicts() throws Exception {
        LocalDateTime lastModifiedAt = value("SELECT last_modified_at FROM booking WHERE id = ?", LocalDateTime.class, UPCOMING_ID);
        Long lastModifiedBy = value("SELECT last_modified_by_user_id FROM booking WHERE id = ?", Long.class, UPCOMING_ID);
        mockMvc.perform(request(UPCOMING_ID, body(1, ROOM_B, "冲突", "2026-08-22T13:00:00", "2026-08-22T14:00:00", 1,
                        null, null, "调整")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_SLOT_CONFLICT"));
        assertUnchanged(UPCOMING_ID, "原主题", 1, List.of("2026-08-22T11:00", "2026-08-22T11:30"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_slot WHERE booking_id = ? AND room_id = ?", Integer.class,
                UPCOMING_ID, ROOM_B)).isZero();
        assertThat(value("SELECT room_id FROM booking WHERE id = ?", Long.class, UPCOMING_ID)).isEqualTo(ROOM_A);
        assertThat(value("SELECT start_time FROM booking WHERE id = ?", LocalDateTime.class, UPCOMING_ID)).isEqualTo(LocalDateTime.of(2026, 8, 22, 11, 0));
        assertThat(value("SELECT end_time FROM booking WHERE id = ?", LocalDateTime.class, UPCOMING_ID)).isEqualTo(LocalDateTime.of(2026, 8, 22, 12, 0));
        assertThat(value("SELECT last_modified_at FROM booking WHERE id = ?", LocalDateTime.class, UPCOMING_ID)).isEqualTo(lastModifiedAt);
        assertThat(value("SELECT last_modified_by_user_id FROM booking WHERE id = ?", Long.class, UPCOMING_ID)).isEqualTo(lastModifiedBy);
    }

    @Test
    void acceptsScheduleExactlyThirteenDaysFromToday() throws Exception {
        mockMvc.perform(request(UPCOMING_ID, body(1, ROOM_B, "十三天边界", "2026-09-04T13:00:00", "2026-09-04T14:00:00", 1,
                        null, null, "调整")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        assertThat(value("SELECT start_time FROM booking WHERE id = ?", LocalDateTime.class, UPCOMING_ID))
                .isEqualTo(LocalDateTime.of(2026, 9, 4, 13, 0));
        assertThat(slotStarts(UPCOMING_ID)).containsExactly("2026-09-04T13:00", "2026-09-04T13:30");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id = ? AND operation_type = 'UPDATE'", Integer.class,
                UPCOMING_ID)).isEqualTo(1);
    }

    @Test
    void appliesRoomRulesOnlyWhenScheduleChanges() throws Exception {
        mockMvc.perform(request(UPCOMING_ID, body(1, 999999L, "不存在房间", "2026-08-22T13:00:00", "2026-08-22T14:00:00", 1,
                        null, null, "调整")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("MEETING_ROOM_NOT_FOUND"));
        mockMvc.perform(request(UPCOMING_ID, body(1, DISABLED_ROOM, "停用房间", "2026-08-22T13:00:00", "2026-08-22T14:00:00", 1,
                        null, null, "调整")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("MEETING_ROOM_DISABLED"));
        mockMvc.perform(request(DISABLED_OLD_ROOM_ID, body(1, DISABLED_ROOM, "旧房间可编辑", "2026-08-22T16:00:00", "2026-08-22T17:00:00", 1,
                        null, null, "调整")))
                .andExpect(status().isOk());
        assertUnchanged(UPCOMING_ID, "原主题", 1, List.of("2026-08-22T11:00", "2026-08-22T11:30"));
    }

    @Test
    void reusesTimeRulesOnlyForScheduleChanges() throws Exception {
        assertErrorForSchedule("2026-09-05T11:00:00", "2026-09-05T12:00:00", "BOOKING_WINDOW_EXCEEDED");
        assertErrorForSchedule("2026-08-22T09:00:00", "2026-08-22T10:00:00", "BOOKING_TIME_INVALID");
        assertErrorForSchedule("2026-08-22T13:15:00", "2026-08-22T14:00:00", "BOOKING_TIME_INVALID");
        assertErrorForSchedule("2026-08-22T23:30:00", "2026-08-23T00:30:00", "BOOKING_CROSS_DAY_NOT_ALLOWED");
        assertErrorForSchedule("2026-08-22T13:00:00", "2026-08-22T13:15:00", "BOOKING_TIME_INVALID");
        assertErrorForSchedule("2026-08-22T13:00:00", "2026-08-22T18:30:00", "BOOKING_DURATION_EXCEEDED");
        assertUnchanged(UPCOMING_ID, "原主题", 1, List.of("2026-08-22T11:00", "2026-08-22T11:30"));
    }

    @Test
    void permitsLegacyOutsideWindowNonScheduleUpdateButRejectsScheduleChange() throws Exception {
        mockMvc.perform(request(OUTSIDE_WINDOW_ID, body(1, ROOM_A, "遗留预约可编辑", "2026-09-10T11:00:00", "2026-09-10T12:00:00", 2,
                        "参会人", "说明", "调整")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(request(OUTSIDE_WINDOW_ID, body(2, ROOM_A, "不得改期", "2026-09-10T11:30:00", "2026-09-10T12:30:00", 2,
                        null, null, "调整")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errorCode").value("BOOKING_WINDOW_EXCEEDED"));
    }

    @Test
    void updatesInProgressDetailsWithoutChangingScheduleOrSlotsAndWritesAudit() throws Exception {
        mockMvc.perform(request(IN_PROGRESS_ID, body(1, null, "进行中新主题", null, null, 3, "参会人", "说明", "现场调整")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.displayStatus").value("IN_PROGRESS"));
        assertThat(slotStarts(IN_PROGRESS_ID)).containsExactly("2026-08-22T10:00", "2026-08-22T10:30", "2026-08-22T11:00", "2026-08-22T11:30");
        assertCompleteAudit(IN_PROGRESS_ID, ROOM_B, ROOM_B, "2026-08-22T10:00:00", "2026-08-22T12:00:00", false,
                "现场调整", "进行中原主题", "进行中新主题", 1, 2);
    }

    @Test
    void rejectsAnyScheduleFieldForInProgressBooking() throws Exception {
        for (String requestBody : List.of(
                body(1, ROOM_A, "不得修改", null, null, 1, null, null, "调整"),
                body(1, null, "不得修改", "2026-08-22T10:00:00", null, 1, null, null, "调整"),
                body(1, null, "不得修改", null, "2026-08-22T12:00:00", 1, null, null, "调整"),
                body(1, ROOM_A, "不得修改", "2026-08-22T10:00:00", "2026-08-22T12:00:00", 1, null, null, "调整"))) {
            mockMvc.perform(request(IN_PROGRESS_ID, requestBody)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("BOOKING_STARTED_TIME_FIELDS_IMMUTABLE"));
        }
        assertUnchanged(IN_PROGRESS_ID, "进行中原主题", 1,
                List.of("2026-08-22T10:00", "2026-08-22T10:30", "2026-08-22T11:00", "2026-08-22T11:30"));
    }

    @Test
    void rejectsCancelledEndedAndMissingBookings() throws Exception {
        mockMvc.perform(request(CANCELLED_ID, body(1, ROOM_A, "取消", "2026-08-22T13:00:00", "2026-08-22T14:00:00", 1, null, null, "调整")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("BOOKING_ALREADY_CANCELLED"));
        mockMvc.perform(request(ENDED_ID, body(1, ROOM_A, "结束", "2026-08-22T09:00:00", "2026-08-22T10:00:00", 1, null, null, "调整")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("BOOKING_ALREADY_ENDED"));
        mockMvc.perform(request(123456789L, body(1, ROOM_A, "不存在", "2026-08-22T13:00:00", "2026-08-22T14:00:00", 1, null, null, "调整")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void rejectsStaleVersionForUpcomingThenAcceptsCurrentVersion() throws Exception {
        jdbcTemplate.update("UPDATE booking SET version = 5 WHERE id = ?", UPCOMING_ID);
        mockMvc.perform(request(UPCOMING_ID, body(4, ROOM_A, "过期", "2026-08-22T11:00:00", "2026-08-22T12:00:00", 1, null, null, "调整")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("BOOKING_VERSION_CONFLICT"));
        assertUnchanged(UPCOMING_ID, "原主题", 5, List.of("2026-08-22T11:00", "2026-08-22T11:30"));
        mockMvc.perform(request(UPCOMING_ID, body(5, ROOM_A, "当前版本", "2026-08-22T11:00:00", "2026-08-22T12:00:00", 1, null, null, "调整")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(6));
    }

    @Test
    void rejectsStaleVersionForInProgressThenAcceptsCurrentVersion() throws Exception {
        jdbcTemplate.update("UPDATE booking SET version = 5 WHERE id = ?", IN_PROGRESS_ID);
        mockMvc.perform(request(IN_PROGRESS_ID, body(4, null, "过期", null, null, 1, null, null, "调整")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("BOOKING_VERSION_CONFLICT"));
        assertUnchanged(IN_PROGRESS_ID, "进行中原主题", 5,
                List.of("2026-08-22T10:00", "2026-08-22T10:30", "2026-08-22T11:00", "2026-08-22T11:30"));
        mockMvc.perform(request(IN_PROGRESS_ID, body(5, null, "当前版本", null, null, 1, null, null, "调整")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(6));
    }

    @Test
    void rejectsInvalidHttpBodiesBeforeMutation() throws Exception {
        List<String> invalidBodies = List.of(
                "{\"roomId\":986010,\"subject\":\"主题\",\"startTime\":\"2026-08-22T11:00:00\",\"endTime\":\"2026-08-22T12:00:00\"}",
                body(0, ROOM_A, "主题", "2026-08-22T11:00:00", "2026-08-22T12:00:00", 1, null, null, "调整"),
                body(1, ROOM_A, " ", "2026-08-22T11:00:00", "2026-08-22T12:00:00", 1, null, null, "调整"),
                body(1, ROOM_A, "a".repeat(201), "2026-08-22T11:00:00", "2026-08-22T12:00:00", 1, null, null, "调整"),
                body(1, 0L, "主题", "2026-08-22T11:00:00", "2026-08-22T12:00:00", 1, null, null, "调整"),
                body(1, ROOM_A, "主题", "2026-08-22T11:00:00", "2026-08-22T12:00:00", -1, null, null, "调整"),
                body(1, ROOM_A, "主题", "2026-08-22T11:00:00", "2026-08-22T12:00:00", 65536, null, null, "调整"),
                body(1, ROOM_A, "主题", "2026-08-22T11:00:00", "2026-08-22T12:00:00", 1, "a".repeat(2001), null, "调整"),
                body(1, ROOM_A, "主题", "2026-08-22T11:00:00", "2026-08-22T12:00:00", 1, null, "a".repeat(4001), "调整"),
                body(1, ROOM_A, "主题", "2026-08-22T11:00:00", "2026-08-22T12:00:00", 1, null, null, "\"" + "a".repeat(501) + "\""));
        for (String invalidBody : invalidBodies) {
            mockMvc.perform(request(UPCOMING_ID, invalidBody)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
        }
        mockMvc.perform(request(UPCOMING_ID, body(1, ROOM_A, "主题", "2026-08-22T11:00:00", "2026-08-22T12:00:00", 1,
                        null, null, "调整").replace("}", ",\"unknown\":true}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("REQUEST_BODY_INVALID"));
        assertUnchanged(UPCOMING_ID, "原主题", 1, List.of("2026-08-22T11:00", "2026-08-22T11:30"));
    }

    private void assertErrorForSchedule(String startTime, String endTime, String errorCode) throws Exception {
        mockMvc.perform(request(UPCOMING_ID, body(1, ROOM_B, "非法时间", startTime, endTime, 1, null, null, "调整")))
                .andExpect(status().is4xxClientError()).andExpect(jsonPath("$.errorCode").value(errorCode));
    }

    private void assertCompleteAudit(long bookingId, long oldRoom, long newRoom, String oldStart, String oldEnd,
            boolean scheduleChanged, String reason, String beforeSubject, String afterSubject, int beforeVersion, int afterVersion) {
        String prefix = "SELECT ";
        assertThat(value(prefix + "operation_type FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo("UPDATE");
        assertThat(value(prefix + "actor_user_id FROM booking_audit_log WHERE booking_id = ?", Long.class, bookingId)).isEqualTo(ADMIN_ID);
        assertThat(value(prefix + "actor_role_snapshot FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo("ADMIN");
        assertThat(value(prefix + "target_owner_user_id FROM booking_audit_log WHERE booking_id = ?", Long.class, bookingId)).isEqualTo(OWNER_ID);
        assertThat(value(prefix + "reason FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo(reason);
        assertThat(value(prefix + "version_before FROM booking_audit_log WHERE booking_id = ?", Integer.class, bookingId)).isEqualTo(beforeVersion);
        assertThat(value(prefix + "version_after FROM booking_audit_log WHERE booking_id = ?", Integer.class, bookingId)).isEqualTo(afterVersion);
        assertThat(value(prefix + "JSON_UNQUOTE(before_json->'$.id') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo(String.valueOf(bookingId));
        assertThat(value(prefix + "JSON_UNQUOTE(before_json->'$.bookingNo') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo("ADMIN-UPDATE-" + bookingId);
        assertThat(value(prefix + "JSON_UNQUOTE(before_json->'$.roomId') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo(String.valueOf(oldRoom));
        assertThat(value(prefix + "JSON_UNQUOTE(after_json->'$.roomId') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo(String.valueOf(newRoom));
        assertThat(value(prefix + "JSON_UNQUOTE(before_json->'$.organizerUserId') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo(String.valueOf(OWNER_ID));
        assertThat(value(prefix + "JSON_UNQUOTE(after_json->'$.organizerName') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo("预约人");
        assertThat(value(prefix + "JSON_UNQUOTE(before_json->'$.subject') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo(beforeSubject);
        assertThat(value(prefix + "JSON_UNQUOTE(after_json->'$.subject') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo(afterSubject);
        assertThat(value(prefix + "JSON_UNQUOTE(before_json->'$.attendeeCount') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo("1");
        assertThat(value(prefix + "JSON_UNQUOTE(before_json->'$.participantsText') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo("原参会人");
        assertThat(value(prefix + "JSON_UNQUOTE(before_json->'$.description') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo("原说明");
        assertThat(value(prefix + "JSON_UNQUOTE(after_json->'$.attendeeCount') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId))
                .isEqualTo(String.valueOf(value("SELECT attendee_count FROM booking WHERE id = ?", Integer.class, bookingId)));
        assertThat(value(prefix + "JSON_UNQUOTE(after_json->'$.participantsText') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId))
                .isEqualTo(value("SELECT participants_text FROM booking WHERE id = ?", String.class, bookingId));
        assertThat(value(prefix + "JSON_UNQUOTE(after_json->'$.description') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId))
                .isEqualTo(value("SELECT description FROM booking WHERE id = ?", String.class, bookingId));
        assertThat(value(prefix + "JSON_UNQUOTE(before_json->'$.startTime') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo(oldStart);
        assertThat(value(prefix + "JSON_UNQUOTE(before_json->'$.endTime') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo(oldEnd);
        assertThat(value(prefix + "JSON_UNQUOTE(after_json->'$.status') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo("ACTIVE");
        assertThat(value(prefix + "JSON_UNQUOTE(after_json->'$.version') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo(String.valueOf(afterVersion));
        assertThat(value(prefix + "JSON_TYPE(before_json->'$.cancelledAt') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo("NULL");
        assertThat(value(prefix + "JSON_TYPE(before_json->'$.cancelReason') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo("NULL");
        assertThat(value(prefix + "JSON_TYPE(after_json->'$.cancelledAt') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo("NULL");
        assertThat(value(prefix + "JSON_TYPE(after_json->'$.cancelReason') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo("NULL");
        assertThat(value(prefix + "JSON_EXTRACT(slot_change_json, '$.scheduleChanged') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId))
                .isEqualTo(String.valueOf(scheduleChanged));
        assertThat(value(prefix + "JSON_UNQUOTE(slot_change_json->'$.oldRoomId') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo(String.valueOf(oldRoom));
        assertThat(value(prefix + "JSON_UNQUOTE(slot_change_json->'$.newRoomId') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo(String.valueOf(newRoom));
        assertThat(value(prefix + "JSON_UNQUOTE(slot_change_json->'$.oldStartTime') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo(oldStart);
        assertThat(value(prefix + "JSON_UNQUOTE(slot_change_json->'$.oldEndTime') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId)).isEqualTo(oldEnd);
        assertThat(value(prefix + "JSON_UNQUOTE(slot_change_json->'$.newStartTime') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId))
                .isEqualTo(value("SELECT DATE_FORMAT(start_time, '%Y-%m-%dT%H:%i:%s') FROM booking WHERE id = ?", String.class, bookingId));
        assertThat(value(prefix + "JSON_UNQUOTE(slot_change_json->'$.newEndTime') FROM booking_audit_log WHERE booking_id = ?", String.class, bookingId))
                .isEqualTo(value("SELECT DATE_FORMAT(end_time, '%Y-%m-%dT%H:%i:%s') FROM booking WHERE id = ?", String.class, bookingId));
        assertThat(value(prefix + "occurred_at FROM booking_audit_log WHERE booking_id = ?", LocalDateTime.class, bookingId))
                .isEqualTo(LocalDateTime.of(2026, 8, 22, 10, 12));
    }

    private void assertUnchanged(long bookingId, String subject, int version, List<String> expectedSlots) {
        assertThat(value("SELECT subject FROM booking WHERE id = ?", String.class, bookingId)).isEqualTo(subject);
        assertThat(value("SELECT version FROM booking WHERE id = ?", Integer.class, bookingId)).isEqualTo(version);
        assertThat(slotStarts(bookingId)).containsExactlyElementsOf(expectedSlots);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id = ?", Integer.class, bookingId)).isZero();
    }

    private <T> T value(String sql, Class<T> type, long bookingId) {
        return jdbcTemplate.queryForObject(sql, type, bookingId);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(long bookingId, String body) {
        return patch("/api/v1/admin/bookings/{bookingId}", bookingId).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String body(long version, Long roomId, String subject, String startTime, String endTime, Integer attendeeCount,
            String participantsText, String description, String reason) {
        return """
                {"version":%d,"roomId":%s,"subject":%s,"startTime":%s,"endTime":%s,"attendeeCount":%s,
                "participantsText":%s,"description":%s,"reason":%s}
                """.formatted(version, number(roomId), json(subject), json(startTime), json(endTime), number(attendeeCount),
                json(participantsText), json(description), reason == null ? "null" : reason.startsWith("\"") || "null".equals(reason) ? reason : json(reason));
    }

    private String number(Number value) {
        return value == null ? "null" : value.toString();
    }

    private String json(String value) {
        return value == null ? "null" : '"' + value.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"") + '"';
    }

    private List<String> slotStarts(long bookingId) {
        return jdbcTemplate.queryForList("SELECT slot_start FROM booking_slot WHERE booking_id = ? ORDER BY slot_start", LocalDateTime.class, bookingId)
                .stream().map(Object::toString).toList();
    }

    private void insertBooking(long id, long roomId, long organizerId, String subject, String startTime, String endTime, String status, int version) {
        String organizerName = organizerId == ADMIN_ID ? "管理员" : "预约人";
        jdbcTemplate.update("""
                INSERT INTO booking(id, booking_no, room_id, organizer_user_id, organizer_name_snapshot, subject, attendee_count,
                    participants_text, description, start_time, end_time, status, version)
                VALUES (?, ?, ?, ?, ?, ?, 1, '原参会人', '原说明', ?, ?, ?, ?)
                """, id, "ADMIN-UPDATE-" + id, roomId, organizerId, organizerName, subject, startTime, endTime, status, version);
    }

    private void insertSlots(long bookingId, long roomId, String... starts) {
        for (String start : starts) {
            jdbcTemplate.update("INSERT INTO booking_slot(booking_id, room_id, slot_start, occupancy_state) VALUES (?, ?, ?, 'ACTIVE')",
                    bookingId, roomId, start);
        }
    }

    private void removeFixture() {
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE booking_id BETWEEN 986101 AND 986108");
        jdbcTemplate.update("DELETE FROM booking_slot WHERE booking_id BETWEEN 986101 AND 986108");
        jdbcTemplate.update("DELETE FROM booking WHERE id BETWEEN 986101 AND 986108");
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id BETWEEN 986010 AND 986012");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", ADMIN_ID, OWNER_ID);
    }

    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(Instant.parse("2026-08-22T02:12:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
