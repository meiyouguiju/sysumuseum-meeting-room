package edu.sysu.museummeetingroom.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.sysu.museummeetingroom.booking.command.CreateBookingCommand;
import edu.sysu.museummeetingroom.booking.dto.CreateBookingResult;
import edu.sysu.museummeetingroom.booking.service.BookingService;
import edu.sysu.museummeetingroom.booking.service.BookingTimeRuleValidator;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = "local.current-user-id=940001")
@Import(BookingServiceIntegrationTest.FixedClockConfiguration.class)
class BookingServiceIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 22, 10, 15);
    private static final long ENABLED_ROOM_ID = 950001L;
    private static final long DISABLED_ROOM_ID = 950002L;
    private static final long OTHER_ENABLED_ROOM_ID = 950003L;

    private final BookingService bookingService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    BookingServiceIntegrationTest(BookingService bookingService, JdbcTemplate jdbcTemplate) {
        this.bookingService = bookingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        removeFixture();
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, role_code, status)
                VALUES (940001, 'TEST', 'booking-user', 'booking-user', '预约测试用户', 'USER', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO meeting_room(id, name, location, capacity, status, sort_order)
                VALUES (950001, '创建测试会议室', '测试地点', 10, 'ENABLED', 1),
                       (950002, '停用创建测试会议室', '测试地点', 10, 'DISABLED', 2),
                       (950003, '第二创建测试会议室', '测试地点', 10, 'ENABLED', 3)
                """);
    }

    @AfterEach
    void tearDown() {
        removeFixture();
    }

    @Test
    void createsBookingSlotsAndCreateAuditWithNormalizedSubject() {
        CreateBookingResult result = bookingService.create(command("  正常预约  ", at(11, 0), at(12, 30), 8));

        assertThat(result.subject()).isEqualTo("正常预约");
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.warnings()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT subject FROM booking WHERE id = ?", String.class, result.id()))
                .isEqualTo("正常预约");
        assertThat(jdbcTemplate.queryForObject("SELECT organizer_user_id FROM booking WHERE id = ?", Long.class, result.id()))
                .isEqualTo(940001L);
        assertThat(jdbcTemplate.queryForList("SELECT slot_start FROM booking_slot WHERE booking_id = ? ORDER BY slot_start", result.id()))
                .extracting(row -> ((LocalDateTime) row.get("slot_start")).toString())
                .containsExactly("2026-08-22T11:00", "2026-08-22T11:30", "2026-08-22T12:00");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id = ? AND operation_type = 'CREATE'", Integer.class, result.id()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT before_json IS NULL FROM booking_audit_log WHERE booking_id = ?", Boolean.class, result.id()))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT actor_user_id FROM booking_audit_log WHERE booking_id = ?", Long.class, result.id()))
                .isEqualTo(940001L);
        assertThat(jdbcTemplate.queryForObject("SELECT actor_role_snapshot FROM booking_audit_log WHERE booking_id = ?", String.class, result.id()))
                .isEqualTo("USER");
        assertThat(jdbcTemplate.queryForObject("SELECT target_owner_user_id FROM booking_audit_log WHERE booking_id = ?", Long.class, result.id()))
                .isEqualTo(940001L);
        assertThat(jdbcTemplate.queryForObject("SELECT version_before IS NULL FROM booking_audit_log WHERE booking_id = ?", Boolean.class, result.id()))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT version_after FROM booking_audit_log WHERE booking_id = ?", Integer.class, result.id()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT after_json->>'$.participantsText' FROM booking_audit_log WHERE booking_id = ?", String.class, result.id()))
                .isEqualTo("参会人员");
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_LENGTH(slot_change_json) FROM booking_audit_log WHERE booking_id = ?", Integer.class, result.id()))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT occurred_at FROM booking_audit_log WHERE booking_id = ?", LocalDateTime.class, result.id()))
                .isEqualTo(NOW);
    }

    @Test
    void allowsNullAttendeeCountAndReturnsCapacityWarningWhenNeeded() {
        CreateBookingResult withoutCount = bookingService.create(command("无人数", at(11, 0), at(11, 30), null));
        CreateBookingResult overCapacity = bookingService.create(command("超容量", at(12, 0), at(12, 30), 11));

        assertThat(withoutCount.attendeeCount()).isNull();
        assertThat(overCapacity.warnings()).containsExactly(new CreateBookingResult.Warning("ROOM_CAPACITY_EXCEEDED", "预计人数超过会议室容量"));
    }

    @Test
    void rejectsInvalidRoomsAndAllTimeRules() {
        assertError(commandWithRoom(999999L, at(11, 0), at(11, 30)), "MEETING_ROOM_NOT_FOUND");
        assertError(commandWithRoom(DISABLED_ROOM_ID, at(11, 0), at(11, 30)), "MEETING_ROOM_DISABLED");
        assertError(command("无效", at(11, 0), at(11, 0), 1), "BOOKING_TIME_INVALID");
        assertError(command("无效", at(11, 15), at(11, 30), 1), "BOOKING_TIME_INVALID");
        assertError(command("无效", at(9, 30), at(10, 0), 1), "BOOKING_TIME_INVALID");
        assertError(command("跨日", at(23, 30), LocalDateTime.of(2026, 8, 23, 0, 0), 1), "BOOKING_CROSS_DAY_NOT_ALLOWED");
        assertError(command("过短", at(11, 0), at(11, 0).plusMinutes(15), 1), "BOOKING_TIME_INVALID");
        assertError(command("超长", at(11, 0), at(16, 30), 1), "BOOKING_DURATION_EXCEEDED");
    }

    @Test
    void permitsOnlyTheCurrentHalfHourSlotOrLaterAtCreationTime() {
        BookingTimeRuleValidator validator = new BookingTimeRuleValidator();
        LocalDateTime day = LocalDateTime.of(2026, 8, 22, 15, 0);

        validator.validate(command("15:01当前槽", day, day.plusMinutes(30), 1), day.plusMinutes(1));
        validator.validate(command("15:29当前槽", day, day.plusMinutes(30), 1), day.plusMinutes(29));
        validator.validate(command("15:30当前槽", day.plusMinutes(30), day.plusHours(1), 1), day.plusMinutes(30));
        assertThatThrownBy(() -> validator.validate(
                        command("15:30旧槽", day, day.plusMinutes(30), 1),
                        day.plusMinutes(30)))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo("BOOKING_TIME_INVALID");
    }

    @Test
    void appliesTodayWindowWeekendAndAdjacentTimeRules() {
        assertThat(bookingService.create(command("今天", at(11, 0), at(11, 30), 1)).id()).isNotNull();
        assertThat(bookingService.create(command("第十四日", LocalDateTime.of(2026, 9, 4, 11, 0), LocalDateTime.of(2026, 9, 4, 11, 30), 1)).id()).isNotNull();
        assertError(command("超窗口", LocalDateTime.of(2026, 9, 5, 11, 0), LocalDateTime.of(2026, 9, 5, 11, 30), 1), "BOOKING_WINDOW_EXCEEDED");
        assertThat(bookingService.create(command("周末", LocalDateTime.of(2026, 8, 23, 11, 0), LocalDateTime.of(2026, 8, 23, 11, 30), 1)).id()).isNotNull();
        bookingService.create(command("相邻前", at(13, 0), at(14, 0), 1));
        assertThat(bookingService.create(command("相邻后", at(14, 0), at(15, 0), 1)).id()).isNotNull();
    }

    @Test
    void rollsBackBookingSlotsAndAuditOnSlotConflictAndAllowsOtherRoom() {
        bookingService.create(command("首个", at(11, 0), at(12, 0), 1));

        assertError(command("冲突", at(11, 30), at(12, 30), 1), "BOOKING_SLOT_CONFLICT");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_slot WHERE room_id = ?", Integer.class, ENABLED_ROOM_ID)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking WHERE subject = '冲突'", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_slot WHERE booking_id IN (SELECT id FROM booking WHERE subject = '冲突')", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE after_json->>'$.subject' = '冲突'", Integer.class)).isZero();
        assertThat(bookingService.create(commandWithRoom(OTHER_ENABLED_ROOM_ID, at(11, 30), at(12, 30))).id()).isNotNull();
    }

    @Test
    void rejectsDisabledCurrentUser() {
        jdbcTemplate.update("UPDATE sys_user SET status = 'DISABLED' WHERE id = ?", 940001L);

        assertError(command("停用用户", at(11, 0), at(11, 30), 1), "UNAUTHENTICATED");
    }

    private void assertError(CreateBookingCommand command, String errorCode) {
        assertThatThrownBy(() -> bookingService.create(command))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode())
                .isEqualTo(errorCode);
    }

    private CreateBookingCommand command(String subject, LocalDateTime startTime, LocalDateTime endTime, Integer attendeeCount) {
        return new CreateBookingCommand(ENABLED_ROOM_ID, subject, startTime, endTime, attendeeCount, "参会人员", "会议说明");
    }

    private CreateBookingCommand commandWithRoom(long roomId, LocalDateTime startTime, LocalDateTime endTime) {
        return new CreateBookingCommand(roomId, "会议", startTime, endTime, 1, null, null);
    }

    private LocalDateTime at(int hour, int minute) {
        return NOW.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
    }

    private void removeFixture() {
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE booking_id IN (SELECT id FROM booking WHERE room_id IN (?, ?, ?))", ENABLED_ROOM_ID, DISABLED_ROOM_ID, OTHER_ENABLED_ROOM_ID);
        jdbcTemplate.update("DELETE FROM booking_slot WHERE room_id IN (?, ?, ?)", ENABLED_ROOM_ID, DISABLED_ROOM_ID, OTHER_ENABLED_ROOM_ID);
        jdbcTemplate.update("DELETE FROM booking WHERE room_id IN (?, ?, ?)", ENABLED_ROOM_ID, DISABLED_ROOM_ID, OTHER_ENABLED_ROOM_ID);
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id IN (?, ?, ?)", ENABLED_ROOM_ID, DISABLED_ROOM_ID, OTHER_ENABLED_ROOM_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", 940001L);
    }

    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(Instant.parse("2026-08-22T02:15:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
