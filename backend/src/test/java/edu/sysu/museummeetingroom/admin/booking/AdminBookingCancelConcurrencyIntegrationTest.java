package edu.sysu.museummeetingroom.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;

import edu.sysu.museummeetingroom.admin.booking.service.AdminBookingCancelService;
import edu.sysu.museummeetingroom.admin.booking.service.AdminBookingUpdateService;
import edu.sysu.museummeetingroom.admin.booking.command.AdminUpdateBookingCommand;
import edu.sysu.museummeetingroom.booking.mutation.service.BookingCancelService;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;
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
@TestPropertySource(properties = "local.current-user-id=987301")
@Import(AdminBookingCancelConcurrencyIntegrationTest.FixedClockConfiguration.class)
class AdminBookingCancelConcurrencyIntegrationTest {
    @Autowired private AdminBookingCancelService service;
    @Autowired private BookingCancelService bookingCancelService;
    @Autowired private AdminBookingUpdateService adminBookingUpdateService;
    @Autowired private JdbcTemplate jdbc;
    @BeforeEach void setup() {
        clean();
        jdbc.update("INSERT INTO sys_user(id,auth_provider,external_subject,login_name,display_name,role_code,status) VALUES (987301,'T','a','a','A','ADMIN','ACTIVE')");
        jdbc.update("INSERT INTO meeting_room(id,name,location,capacity,status,sort_order) VALUES (987310,'R','L',10,'ENABLED',1)");
        jdbc.update("INSERT INTO booking(id,booking_no,room_id,organizer_user_id,organizer_name_snapshot,subject,start_time,end_time,status,version) VALUES (987311,'C',987310,987301,'A','S','2026-08-22 11:00:00','2026-08-22 12:00:00','ACTIVE',1)");
        jdbc.update("INSERT INTO booking_slot(booking_id,room_id,slot_start,occupancy_state) VALUES (987311,987310,'2026-08-22 11:00:00','ACTIVE')");
    }
    @AfterEach void cleanup() { clean(); }
    @Test void concurrentAdminCancelsAllowExactlyOne() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = List.of(executor.submit(() -> call(ready, start)), executor.submit(() -> call(ready, start)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> outcomes = List.of(results.get(0).get(10, TimeUnit.SECONDS), results.get(1).get(10, TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", "BOOKING_ALREADY_CANCELLED");
            assertThat(jdbc.queryForObject("SELECT version FROM booking WHERE id=987311", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id=987311 AND operation_type='CANCEL'", Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
    @Test void adminAndOwnerCancelAllowExactlyOne() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> admin = executor.submit(() -> call(ready, start));
            Future<String> owner = executor.submit(() -> ordinaryCall(ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> outcomes = List.of(admin.get(10, TimeUnit.SECONDS), owner.get(10, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter("SUCCESS"::equals)).hasSize(1);
            assertThat(outcomes.stream().filter(value -> !"SUCCESS".equals(value))).allMatch(value -> "BOOKING_ALREADY_CANCELLED".equals(value));
            assertThat(jdbc.queryForObject("SELECT version FROM booking WHERE id=987311", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id=987311 AND operation_type='CANCEL'", Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
    @Test void adminCancelAndAdminUpdateAllowOneMutation() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> cancel = executor.submit(() -> call(ready, start));
            Future<String> update = executor.submit(() -> updateCall(ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> outcomes = List.of(cancel.get(10, TimeUnit.SECONDS), update.get(10, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter("SUCCESS"::equals)).hasSize(1);
            assertThat(outcomes.stream().filter(value -> !"SUCCESS".equals(value))).allMatch(value -> "BOOKING_ALREADY_CANCELLED".equals(value) || "BOOKING_VERSION_CONFLICT".equals(value));
            assertThat(jdbc.queryForObject("SELECT version FROM booking WHERE id=987311", Integer.class)).isEqualTo(2);
            int updates = jdbc.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id=987311 AND operation_type='UPDATE'", Integer.class);
            int cancels = jdbc.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id=987311 AND operation_type='CANCEL'", Integer.class);
            assertThat(updates + cancels).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
    private String call(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            service.cancel(987311, 1, null);
            return "SUCCESS";
        } catch (ApiException exception) {
            return exception.errorCode();
        }
    }
    private String ordinaryCall(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            bookingCancelService.cancel(987311, 1, null);
            return "SUCCESS";
        } catch (ApiException exception) {
            return exception.errorCode();
        }
    }
    private String updateCall(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            adminBookingUpdateService.update(987311, new AdminUpdateBookingCommand(1, 987310L, "新主题", LocalDateTime.of(2026, 8, 22, 11, 0), LocalDateTime.of(2026, 8, 22, 12, 0), 1, null, null, null));
            return "SUCCESS";
        } catch (ApiException exception) {
            return exception.errorCode();
        }
    }
    private void clean() {
        jdbc.update("DELETE FROM booking_audit_log WHERE booking_id=987311");
        jdbc.update("DELETE FROM booking_slot WHERE booking_id=987311");
        jdbc.update("DELETE FROM booking WHERE id=987311");
        jdbc.update("DELETE FROM meeting_room WHERE id=987310");
        jdbc.update("DELETE FROM sys_user WHERE id=987301");
    }

    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(Instant.parse("2026-08-22T02:12:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
