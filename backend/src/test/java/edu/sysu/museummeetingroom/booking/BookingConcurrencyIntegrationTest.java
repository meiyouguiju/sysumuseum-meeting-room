package edu.sysu.museummeetingroom.booking;

import static org.assertj.core.api.Assertions.assertThat;

import edu.sysu.museummeetingroom.booking.command.CreateBookingCommand;
import edu.sysu.museummeetingroom.booking.service.BookingService;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
@TestPropertySource(properties = "local.current-user-id=960001")
@Import(BookingConcurrencyIntegrationTest.FixedClockConfiguration.class)
class BookingConcurrencyIntegrationTest {

    private static final long USER_ID = 960001L;
    private static final long ROOM_ID = 970001L;

    private final BookingService bookingService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    BookingConcurrencyIntegrationTest(BookingService bookingService, JdbcTemplate jdbcTemplate) {
        this.bookingService = bookingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        removeFixture();
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, role_code, status)
                VALUES (960001, 'TEST', 'concurrency-user', 'concurrency-user', '并发测试用户', 'USER', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO meeting_room(id, name, location, capacity, status, sort_order)
                VALUES (970001, '并发测试会议室', '测试地点', 10, 'ENABLED', 1)
                """);
    }

    @AfterEach
    void tearDown() {
        removeFixture();
    }

    @Test
    void databaseUniqueConstraintAllowsExactlyOneConcurrentBooking() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = List.of(
                    executor.submit(() -> createWhenStarted("并发预约一", ready, start)),
                    executor.submit(() -> createWhenStarted("并发预约二", ready, start)));
            ready.await();
            start.countDown();

            List<String> outcomes = List.of(results.get(0).get(), results.get(1).get());
            assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS", "BOOKING_SLOT_CONFLICT");
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking WHERE room_id = ?", Integer.class, ROOM_ID)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_slot WHERE room_id = ?", Integer.class, ROOM_ID)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private String createWhenStarted(String subject, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            bookingService.create(new CreateBookingCommand(
                    ROOM_ID,
                    subject,
                    LocalDateTime.of(2026, 8, 22, 11, 0),
                    LocalDateTime.of(2026, 8, 22, 12, 0),
                    1,
                    null,
                    null));
            return "SUCCESS";
        } catch (ApiException exception) {
            return exception.errorCode();
        }
    }

    private void removeFixture() {
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE booking_id IN (SELECT id FROM booking WHERE room_id = ?)", ROOM_ID);
        jdbcTemplate.update("DELETE FROM booking_slot WHERE room_id = ?", ROOM_ID);
        jdbcTemplate.update("DELETE FROM booking WHERE room_id = ?", ROOM_ID);
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id = ?", ROOM_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", USER_ID);
    }

    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(Instant.parse("2026-08-22T02:15:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
