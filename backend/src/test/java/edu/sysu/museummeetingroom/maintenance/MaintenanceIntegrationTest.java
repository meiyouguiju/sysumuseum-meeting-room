package edu.sysu.museummeetingroom.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(MaintenanceIntegrationTest.FixedClockConfiguration.class)
@TestPropertySource(properties = {
        "maintenance.slot-cleanup.batch-size=2",
        "maintenance.idempotency.cleanup-batch-size=2",
        "maintenance.idempotency.processing-recovery-batch-size=100"})
class MaintenanceIntegrationTest {

    private final SlotCleanupService slotCleanupService;
    private final ProcessingRecoveryService processingRecoveryService;
    private final ProcessingRecoveryTransactionService processingRecoveryTransactionService;
    private final IdempotencyCleanupService idempotencyCleanupService;
    private final MaintenanceIdempotencyMapper maintenanceIdempotencyMapper;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    MaintenanceIntegrationTest(
            SlotCleanupService slotCleanupService,
            ProcessingRecoveryService processingRecoveryService,
            ProcessingRecoveryTransactionService processingRecoveryTransactionService,
            IdempotencyCleanupService idempotencyCleanupService,
            MaintenanceIdempotencyMapper maintenanceIdempotencyMapper,
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbcTemplate) {
        this.slotCleanupService = slotCleanupService;
        this.processingRecoveryService = processingRecoveryService;
        this.processingRecoveryTransactionService = processingRecoveryTransactionService;
        this.idempotencyCleanupService = idempotencyCleanupService;
        this.maintenanceIdempotencyMapper = maintenanceIdempotencyMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void cleansOnlyPastSlotsInBatchesWithoutChangingBookingHistory() {
        insertBooking(881001L, "MAINTENANCE-BOOKING-ONE");
        insertBooking(881002L, "MAINTENANCE-BOOKING-TWO");
        insertSlots();
        insertAudit(881001L);

        assertThat(slotCleanupService.cleanupPastSlots()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_slot", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_slot WHERE slot_start = '2026-08-23 10:00:00'", Integer.class)).isEqualTo(1);
    }

    @Test
    void recoversOnlyStaleProcessingAndTerminalCleanupNeverDeletesProcessing() {
        insertIdempotency(882001L, "PROCESSING", "2026-08-23 10:07:59", "2026-08-24 10:00:00");
        insertIdempotency(882002L, "PROCESSING", "2026-08-23 10:11:00", "2026-08-22 10:00:00");
        insertIdempotency(882003L, "SUCCEEDED", "2026-08-23 09:00:00", "2026-08-22 10:00:00");
        insertIdempotency(882004L, "FAILED", "2026-08-23 09:00:00", "2026-08-22 10:00:00");

        assertThat(processingRecoveryService.recoverStaleProcessingRecords().recoveredCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT processing_status FROM idempotency_record WHERE id = 882001", String.class)).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject("SELECT response_http_status FROM idempotency_record WHERE id = 882001", Integer.class)).isEqualTo(500);
        assertThat(jdbcTemplate.queryForObject("SELECT failure_code FROM idempotency_record WHERE id = 882001", String.class)).isEqualTo("INTERNAL_ERROR");
        assertThat(jdbcTemplate.queryForObject("SELECT response_body->>'$.errorCode' FROM idempotency_record WHERE id = 882001", String.class)).isEqualTo("INTERNAL_ERROR");
        assertThat(jdbcTemplate.queryForObject("SELECT JSON_LENGTH(response_body->'$.fieldErrors') FROM idempotency_record WHERE id = 882001", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT completed_at IS NOT NULL FROM idempotency_record WHERE id = 882001", Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT booking_id IS NULL FROM idempotency_record WHERE id = 882001", Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT processing_status FROM idempotency_record WHERE id = 882002", String.class)).isEqualTo("PROCESSING");

        assertThat(idempotencyCleanupService.cleanupExpiredTerminalRecords()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE id = 882002", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE id IN (882003, 882004)", Integer.class)).isZero();
    }

    @Test
    void recoverySkipsARecordThatBecomesSucceededWhileItsRowLockIsHeld() throws Exception {
        insertIdempotency(882001L, "PROCESSING", "2026-08-23 10:07:59", "2026-08-24 10:00:00");
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch completeBusinessTransaction = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> businessTransaction = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                maintenanceIdempotencyMapper.lockById(882001L);
                lockHeld.countDown();
                await(completeBusinessTransaction);
                jdbcTemplate.update("UPDATE idempotency_record SET processing_status = 'SUCCEEDED' WHERE id = 882001");
            }));
            lockHeld.await();
            Future<?> recovery = executor.submit(() -> processingRecoveryService.recoverStaleProcessingRecords());

            completeBusinessTransaction.countDown();
            businessTransaction.get();
            recovery.get();

            assertThat(jdbcTemplate.queryForObject("SELECT processing_status FROM idempotency_record WHERE id = 882001", String.class))
                    .isEqualTo("SUCCEEDED");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentRecoveryTransactionsAllowExactlyOneTransitionToFailed() throws Exception {
        insertIdempotency(882001L, "PROCESSING", "2026-08-23 10:07:59", "2026-08-24 10:00:00");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> recoverWhenStarted(ready, start));
            Future<Boolean> second = executor.submit(() -> recoverWhenStarted(ready, start));
            ready.await();
            start.countDown();

            assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
            assertThat(jdbcTemplate.queryForObject("SELECT processing_status FROM idempotency_record WHERE id = 882001", String.class))
                    .isEqualTo("FAILED");
            assertThat(jdbcTemplate.queryForObject("SELECT response_http_status FROM idempotency_record WHERE id = 882001", Integer.class))
                    .isEqualTo(500);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void terminalCleanupUsesStrictExpiryBoundaryAndNeverDeletesProcessing() {
        insertIdempotency(882001L, "SUCCEEDED", "2026-08-23 09:00:00", "2026-08-23 10:11:59");
        insertIdempotency(882002L, "FAILED", "2026-08-23 09:00:00", "2026-08-23 10:12:00");
        insertIdempotency(882003L, "SUCCEEDED", "2026-08-23 09:00:00", "2026-08-23 10:12:01");
        insertIdempotency(882004L, "PROCESSING", "2026-08-23 09:00:00", "2026-08-23 10:11:59");

        idempotencyCleanupService.cleanupExpiredTerminalRecords();

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE id = 882001", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE id IN (882002, 882003, 882004)", Integer.class)).isEqualTo(3);
    }

    private boolean recoverWhenStarted(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        return processingRecoveryTransactionService.recoverIfStillStale(882001L, java.time.LocalDateTime.of(2026, 8, 23, 10, 10));
    }

    private void insertSlots() {
        jdbcTemplate.update("""
                INSERT INTO booking_slot(id, booking_id, room_id, slot_start, occupancy_state)
                VALUES (883001, 881001, 884001, '2026-08-23 09:00:00', 'ACTIVE'),
                       (883002, 881001, 884001, '2026-08-23 09:30:00', 'CANCELLED_CURRENT_SLOT_HOLD'),
                       (883003, 881002, 884002, '2026-08-23 09:00:00', 'ACTIVE'),
                       (883004, 881001, 884002, '2026-08-23 10:00:00', 'ACTIVE'),
                       (883005, 881001, 884002, '2026-08-23 10:30:00', 'ACTIVE'),
                       (883006, 881001, 884002, '2026-08-23 11:00:00', 'ACTIVE')
                """);
    }

    private void insertBooking(long id, String bookingNo) {
        jdbcTemplate.update("""
                INSERT INTO booking(id, booking_no, room_id, organizer_user_id, organizer_name_snapshot, subject, start_time, end_time, status, version)
                VALUES (?, ?, 884001, 885001, '维护测试用户', '维护测试会议', '2026-08-23 09:00:00', '2026-08-23 11:30:00', 'ACTIVE', 1)
                """, id, bookingNo);
    }

    private void insertAudit(long bookingId) {
        jdbcTemplate.update("""
                INSERT INTO booking_audit_log(id, booking_id, operation_type, actor_user_id, actor_role_snapshot, target_owner_user_id, version_after, after_json, occurred_at)
                VALUES (886001, ?, 'CREATE', 885001, 'USER', 885001, 1, JSON_OBJECT('subject', '维护测试会议'), '2026-08-23 09:00:00')
                """, bookingId);
    }

    private void insertIdempotency(long id, String status, String startedAt, String expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO idempotency_record(id, operation_type, user_id, idempotency_key, request_hash, processing_status, processing_started_at, created_at, expires_at, updated_at)
                VALUES (?, 'CREATE_BOOKING', ?, CONCAT('maintenance-', ?), UNHEX(REPEAT('00', 32)), ?, ?, ?, ?, ?)
                """, id, id, id, status, startedAt, startedAt, expiresAt, startedAt);
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM idempotency_record WHERE id BETWEEN 882001 AND 882004");
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE id = 886001");
        jdbcTemplate.update("DELETE FROM booking_slot WHERE id BETWEEN 883001 AND 883006");
        jdbcTemplate.update("DELETE FROM booking WHERE id = 881001");
        jdbcTemplate.update("DELETE FROM booking WHERE id = 881002");
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("维护测试线程被中断", exception);
        }
    }

    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(Instant.parse("2026-08-23T02:12:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
