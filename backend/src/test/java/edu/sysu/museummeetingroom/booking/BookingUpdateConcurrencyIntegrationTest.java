package edu.sysu.museummeetingroom.booking;

import static org.assertj.core.api.Assertions.assertThat;

import edu.sysu.museummeetingroom.booking.command.UpdateBookingCommand;
import edu.sysu.museummeetingroom.booking.mutation.service.BookingUpdateService;
import edu.sysu.museummeetingroom.booking.query.dto.BookingDetailResponse;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
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
@TestPropertySource(properties = "local.current-user-id=981001")
@Import(BookingUpdateConcurrencyIntegrationTest.FixedClockConfiguration.class)
class BookingUpdateConcurrencyIntegrationTest {

    private static final long USER_ID = 981001L;
    private static final long ROOM_A = 981010L;
    private static final long ROOM_B = 981011L;
    private static final long TARGET_ROOM = 981012L;

    private final BookingUpdateService bookingUpdateService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    BookingUpdateConcurrencyIntegrationTest(BookingUpdateService bookingUpdateService, JdbcTemplate jdbcTemplate) {
        this.bookingUpdateService = bookingUpdateService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        clean();
        jdbcTemplate.update("INSERT INTO sys_user(id,auth_provider,external_subject,login_name,display_name,role_code,status) VALUES (981001,'TEST','c2-concurrent','c2-concurrent','并发用户','USER','ACTIVE')");
        jdbcTemplate.update("INSERT INTO meeting_room(id,name,location,capacity,status,sort_order) VALUES (981010,'并发A','测试',10,'ENABLED',1),(981011,'并发B','测试',10,'ENABLED',2),(981012,'并发目标','测试',10,'ENABLED',3)");
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void sameVersionAllowsExactlyOneNonScheduleUpdateAndReturnsItsOwnResponse() throws Exception {
        insertBooking(981101L, ROOM_A, "原主题", "2026-08-22 11:00:00", "2026-08-22 12:00:00");
        insertSlots(981101L, ROOM_A, "2026-08-22 11:00:00", "2026-08-22 11:30:00");
        List<Outcome> outcomes = concurrently(
                command(981101L, ROOM_A, "并发修改A", "2026-08-22T11:00", "2026-08-22T12:00"),
                command(981101L, ROOM_A, "并发修改B", "2026-08-22T11:00", "2026-08-22T12:00"));
        Outcome success = outcomes.stream().filter(Outcome::successful).findFirst().orElseThrow();
        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.stream().filter(Outcome::successful)).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> "BOOKING_VERSION_CONFLICT".equals(outcome.errorCode()))).hasSize(1);
        assertThat(success.response().version()).isEqualTo(2);
        assertThat(success.response().subject()).isEqualTo(success.subject());
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM booking WHERE id=981101", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT subject FROM booking WHERE id=981101", String.class)).isEqualTo(success.subject());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id=981101", Integer.class)).isEqualTo(1);
        assertThat(slotStarts(981101L)).containsExactly("2026-08-22T11:00", "2026-08-22T11:30");
    }

    @Test
    void differentBookingsCompeteForTargetSlotsWithOneCompleteRollback() throws Exception {
        insertBooking(981102L, ROOM_A, "预约A", "2026-08-22 11:00:00", "2026-08-22 12:00:00");
        insertBooking(981103L, ROOM_B, "预约B", "2026-08-22 13:00:00", "2026-08-22 14:00:00");
        insertSlots(981102L, ROOM_A, "2026-08-22 11:00:00", "2026-08-22 11:30:00");
        insertSlots(981103L, ROOM_B, "2026-08-22 13:00:00", "2026-08-22 13:30:00");
        List<Outcome> outcomes = concurrently(
                command(981102L, TARGET_ROOM, "目标A", "2026-08-22T16:00", "2026-08-22T17:00"),
                command(981103L, TARGET_ROOM, "目标B", "2026-08-22T16:00", "2026-08-22T17:00"));
        Outcome failed = outcomes.stream().filter(outcome -> !outcome.successful()).findFirst().orElseThrow();
        assertThat(outcomes.stream().filter(Outcome::successful)).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> "BOOKING_SLOT_CONFLICT".equals(outcome.errorCode()))).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM booking WHERE id=?", Integer.class, failed.bookingId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id=?", Integer.class, failed.bookingId())).isZero();
        assertThat(slotStarts(failed.bookingId())).hasSize(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_slot WHERE room_id=? AND slot_start IN ('2026-08-22 16:00:00','2026-08-22 16:30:00')", Integer.class, TARGET_ROOM)).isEqualTo(2);
    }

    private List<Outcome> concurrently(UpdateRequest first, UpdateRequest second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Outcome>> futures = List.of(executor.submit(task(first, ready, start)), executor.submit(task(second, ready, start)));
            ready.await();
            start.countDown();
            return List.of(futures.get(0).get(), futures.get(1).get());
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Outcome> task(UpdateRequest request, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                return new Outcome(request.bookingId(), request.subject(), bookingUpdateService.update(request.bookingId(), request.command()), null);
            } catch (ApiException exception) {
                return new Outcome(request.bookingId(), request.subject(), null, exception.errorCode());
            }
        };
    }

    private UpdateRequest command(long bookingId, long roomId, String subject, String start, String end) {
        return new UpdateRequest(bookingId, subject, new UpdateBookingCommand(1, roomId, subject, LocalDateTime.parse(start), LocalDateTime.parse(end), 1, null, null));
    }

    private void insertBooking(long id, long roomId, String subject, String start, String end) {
        jdbcTemplate.update("INSERT INTO booking(id,booking_no,room_id,organizer_user_id,organizer_name_snapshot,subject,start_time,end_time,status,version) VALUES (?,?,?,?, '并发用户',?,?,?,'ACTIVE',1)", id, "C2-" + id, roomId, USER_ID, subject, start, end);
    }

    private void insertSlots(long bookingId, long roomId, String... starts) {
        for (String start : starts) {
            jdbcTemplate.update("INSERT INTO booking_slot(booking_id,room_id,slot_start,occupancy_state) VALUES (?,?,?,'ACTIVE')", bookingId, roomId, start);
        }
    }

    private List<String> slotStarts(long bookingId) {
        return jdbcTemplate.queryForList("SELECT slot_start FROM booking_slot WHERE booking_id=? ORDER BY slot_start", LocalDateTime.class, bookingId).stream().map(Object::toString).toList();
    }

    private void clean() {
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE booking_id BETWEEN 981101 AND 981103");
        jdbcTemplate.update("DELETE FROM booking_slot WHERE booking_id BETWEEN 981101 AND 981103");
        jdbcTemplate.update("DELETE FROM booking WHERE id BETWEEN 981101 AND 981103");
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id BETWEEN 981010 AND 981012");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id=981001");
    }

    private record UpdateRequest(long bookingId, String subject, UpdateBookingCommand command) { }
    private record Outcome(long bookingId, String subject, BookingDetailResponse response, String errorCode) { private boolean successful() { return response != null; } }

    static class FixedClockConfiguration { @Bean @Primary Clock fixedBusinessClock() { return Clock.fixed(Instant.parse("2026-08-22T02:15:00Z"), ZoneId.of("Asia/Shanghai")); } }
}
