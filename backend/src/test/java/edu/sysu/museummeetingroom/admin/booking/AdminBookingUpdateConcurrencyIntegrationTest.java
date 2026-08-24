package edu.sysu.museummeetingroom.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;

import edu.sysu.museummeetingroom.admin.booking.command.AdminUpdateBookingCommand;
import edu.sysu.museummeetingroom.admin.booking.service.AdminBookingUpdateService;
import edu.sysu.museummeetingroom.booking.command.UpdateBookingCommand;
import edu.sysu.museummeetingroom.booking.mutation.service.BookingCancelService;
import edu.sysu.museummeetingroom.booking.mutation.service.BookingUpdateService;
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
import java.util.concurrent.TimeUnit;
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
@TestPropertySource(properties = "local.current-user-id=986401")
@Import(AdminBookingUpdateConcurrencyIntegrationTest.FixedClockConfiguration.class)
class AdminBookingUpdateConcurrencyIntegrationTest {

    private static final long ADMIN_ID = 986401L;
    private static final long ROOM_ID = 986410L;
    private static final long BOOKING_ID = 986411L;

    private final AdminBookingUpdateService adminBookingUpdateService;
    private final BookingUpdateService bookingUpdateService;
    private final BookingCancelService bookingCancelService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AdminBookingUpdateConcurrencyIntegrationTest(AdminBookingUpdateService adminBookingUpdateService,
            BookingUpdateService bookingUpdateService, BookingCancelService bookingCancelService, JdbcTemplate jdbcTemplate) {
        this.adminBookingUpdateService = adminBookingUpdateService;
        this.bookingUpdateService = bookingUpdateService;
        this.bookingCancelService = bookingCancelService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        clean();
        jdbcTemplate.update("INSERT INTO sys_user(id,auth_provider,external_subject,login_name,display_name,role_code,status) VALUES (986401,'TEST','d2c-admin','d2c-admin','并发管理员','ADMIN','ACTIVE')");
        jdbcTemplate.update("INSERT INTO meeting_room(id,name,location,capacity,status,sort_order) VALUES (986410,'D2C并发室','测试',20,'ENABLED',1)");
        jdbcTemplate.update("INSERT INTO booking(id,booking_no,room_id,organizer_user_id,organizer_name_snapshot,subject,start_time,end_time,status,version) VALUES (986411,'D2C-CONCURRENT',986410,986401,'并发管理员','原主题','2026-08-22 11:00:00','2026-08-22 12:00:00','ACTIVE',1)");
        jdbcTemplate.update("INSERT INTO booking_slot(booking_id,room_id,slot_start,occupancy_state) VALUES (986411,986410,'2026-08-22 11:00:00','ACTIVE'),(986411,986410,'2026-08-22 11:30:00','ACTIVE')");
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void adminUpdatesWithSameVersionAllowExactlyOneWinner() throws Exception {
        List<Outcome> outcomes = run(() -> admin("管理员A"), () -> admin("管理员B"));
        assertOneUpdateWinner(outcomes, "BOOKING_VERSION_CONFLICT");
    }

    @Test
    void adminUpdateAndOwnerPatchAllowExactlyOneWinner() throws Exception {
        List<Outcome> outcomes = run(() -> admin("管理员修改"), () -> bookingUpdateService.update(BOOKING_ID,
                new UpdateBookingCommand(1, ROOM_ID, "普通PATCH", LocalDateTime.of(2026, 8, 22, 11, 0),
                        LocalDateTime.of(2026, 8, 22, 12, 0), 1, null, null)));
        assertOneUpdateWinner(outcomes, "BOOKING_VERSION_CONFLICT");
    }

    @Test
    void adminUpdateAndCancelAllowExactlyOneMutation() throws Exception {
        List<Outcome> outcomes = run(() -> admin("管理员修改"), () -> bookingCancelService.cancel(BOOKING_ID, 1, null));
        assertThat(outcomes.stream().filter(Outcome::successful)).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful()).map(Outcome::errorCode))
                .allMatch(code -> "BOOKING_VERSION_CONFLICT".equals(code) || "BOOKING_ALREADY_CANCELLED".equals(code));
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM booking WHERE id=986411", Integer.class)).isEqualTo(2);
        int updates = auditCount("UPDATE");
        int cancels = auditCount("CANCEL");
        assertThat(updates + cancels).isEqualTo(1);
        if (updates == 1) {
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM booking WHERE id=986411", String.class)).isEqualTo("ACTIVE");
        } else {
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM booking WHERE id=986411", String.class)).isEqualTo("CANCELLED");
        }
    }

    private void assertOneUpdateWinner(List<Outcome> outcomes, String loserCode) {
        assertThat(outcomes.stream().filter(Outcome::successful)).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> loserCode.equals(outcome.errorCode()))).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM booking WHERE id=986411", Integer.class)).isEqualTo(2);
        assertThat(auditCount("UPDATE")).isEqualTo(1);
        assertThat(auditCount("CANCEL")).isZero();
    }

    private Object admin(String subject) {
        return adminBookingUpdateService.update(BOOKING_ID, new AdminUpdateBookingCommand(1, ROOM_ID, subject,
                LocalDateTime.of(2026, 8, 22, 11, 0), LocalDateTime.of(2026, 8, 22, 12, 0), 1, null, null, null));
    }

    private List<Outcome> run(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Outcome>> futures = List.of(executor.submit(task(first, ready, start)), executor.submit(task(second, ready, start)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(10, TimeUnit.SECONDS), futures.get(1).get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Outcome> task(Callable<?> action, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                action.call();
                return new Outcome(null);
            } catch (ApiException exception) {
                return new Outcome(exception.errorCode());
            } catch (Exception exception) {
                return new Outcome("UNEXPECTED:" + exception.getClass().getSimpleName());
            }
        };
    }

    private int auditCount(String type) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id=986411 AND operation_type=?", Integer.class, type);
    }

    private void clean() {
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE booking_id=986411");
        jdbcTemplate.update("DELETE FROM booking_slot WHERE booking_id=986411");
        jdbcTemplate.update("DELETE FROM booking WHERE id=986411");
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id=986410");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id=986401");
    }

    private record Outcome(String errorCode) {
        private boolean successful() { return errorCode == null; }
    }

    static class FixedClockConfiguration {
        @Bean @Primary Clock fixedBusinessClock() { return Clock.fixed(Instant.parse("2026-08-22T02:12:00Z"), ZoneId.of("Asia/Shanghai")); }
    }
}
