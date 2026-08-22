package edu.sysu.museummeetingroom.booking.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import edu.sysu.museummeetingroom.auth.CurrentUser;
import edu.sysu.museummeetingroom.auth.CurrentUserProvider;
import edu.sysu.museummeetingroom.booking.command.CreateBookingCommand;
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

@SpringBootTest
@ActiveProfiles("local")
@Import(CreateBookingCoordinatorIntegrationTest.TestConfiguration.class)
class CreateBookingCoordinatorIntegrationTest {

    private static final long USER_ONE_ID = 980001L;
    private static final long USER_TWO_ID = 980002L;
    private static final long ROOM_ONE_ID = 990001L;
    private static final long ROOM_TWO_ID = 990002L;

    private final CreateBookingCoordinator createBookingCoordinator;
    private final CreateBookingRequestHasher createBookingRequestHasher;
    private final IdempotencyClaimService idempotencyClaimService;
    private final IdempotencyRecordQueryService idempotencyRecordQueryService;
    private final TestCurrentUserProvider currentUserProvider;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    CreateBookingCoordinatorIntegrationTest(
            CreateBookingCoordinator createBookingCoordinator,
            CreateBookingRequestHasher createBookingRequestHasher,
            IdempotencyClaimService idempotencyClaimService,
            IdempotencyRecordQueryService idempotencyRecordQueryService,
            TestCurrentUserProvider currentUserProvider,
            JdbcTemplate jdbcTemplate) {
        this.createBookingCoordinator = createBookingCoordinator;
        this.createBookingRequestHasher = createBookingRequestHasher;
        this.idempotencyClaimService = idempotencyClaimService;
        this.idempotencyRecordQueryService = idempotencyRecordQueryService;
        this.currentUserProvider = currentUserProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        removeFixture();
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, role_code, status)
                VALUES (980001, 'TEST', 'idempotency-user-one', 'idempotency-user-one', '幂等用户一', 'USER', 'ACTIVE'),
                       (980002, 'TEST', 'idempotency-user-two', 'idempotency-user-two', '幂等用户二', 'USER', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO meeting_room(id, name, location, capacity, status, sort_order)
                VALUES (990001, '幂等测试会议室一', '测试地点', 10, 'ENABLED', 1),
                       (990002, '幂等测试会议室二', '测试地点', 10, 'ENABLED', 2)
                """);
        currentUserProvider.setCurrentUser(userOne());
    }

    @AfterEach
    void tearDown() {
        removeFixture();
    }

    @Test
    void succeedsOnceAndReplaysTheStableSuccessResponse() {
        CreateBookingCoordinationResult first = createBookingCoordinator.create(command(ROOM_ONE_ID, "首次成功"), "key-success");
        CreateBookingCoordinationResult replay = createBookingCoordinator.create(command(ROOM_ONE_ID, "首次成功"), "key-success");

        assertThat(first.status()).isEqualTo(CreateBookingCoordinationStatus.FIRST_SUCCESS);
        assertThat(replay.status()).isEqualTo(CreateBookingCoordinationStatus.REPLAY_SUCCESS);
        assertThat(replay.stableResponseBody()).isEqualTo(first.stableResponseBody());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking WHERE room_id = ?", Integer.class, ROOM_ONE_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_slot WHERE room_id = ?", Integer.class, ROOM_ONE_ID)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log", Integer.class)).isEqualTo(1);

        IdempotencyRecord record = idempotencyRecordQueryService.findCreateBookingRecord(USER_ONE_ID, "key-success");
        assertThat(record.processingStatus()).isEqualTo("SUCCEEDED");
        assertThat(record.bookingId()).isEqualTo(first.bookingResult().id());
        assertThat(record.responseHttpStatus()).isEqualTo(201);
        assertThat(record.responseBody()).isNotBlank();
        assertThat(record.completedAt()).isNotNull();
    }

    @Test
    void rejectsReuseOfTheSameKeyWithDifferentContent() {
        createBookingCoordinator.create(command(ROOM_ONE_ID, "首次"), "key-reused");

        CreateBookingCoordinationResult result = createBookingCoordinator.create(command(ROOM_ONE_ID, "内容不同"), "key-reused");

        assertThat(result.status()).isEqualTo(CreateBookingCoordinationStatus.KEY_REUSED);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking WHERE room_id = ?", Integer.class, ROOM_ONE_ID)).isEqualTo(1);
    }

    @Test
    void persistsAndReplaysDeterministicSlotConflictFailure() {
        createBookingCoordinator.create(command(ROOM_ONE_ID, "已占用"), "key-occupied");

        CreateBookingCoordinationResult firstFailure = createBookingCoordinator.create(command(ROOM_ONE_ID, "冲突"), "key-conflict");
        CreateBookingCoordinationResult replay = createBookingCoordinator.create(command(ROOM_ONE_ID, "冲突"), "key-conflict");

        assertThat(firstFailure.status()).isEqualTo(CreateBookingCoordinationStatus.FIRST_FAILURE);
        assertThat(firstFailure.failureCode()).isEqualTo("BOOKING_SLOT_CONFLICT");
        assertThat(replay.status()).isEqualTo(CreateBookingCoordinationStatus.REPLAY_FAILURE);
        assertThat(replay.failureCode()).isEqualTo("BOOKING_SLOT_CONFLICT");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking WHERE room_id = ?", Integer.class, ROOM_ONE_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log", Integer.class)).isEqualTo(1);

        IdempotencyRecord record = idempotencyRecordQueryService.findCreateBookingRecord(USER_ONE_ID, "key-conflict");
        assertThat(record.processingStatus()).isEqualTo("FAILED");
        assertThat(record.bookingId()).isNull();
        assertThat(record.failureCode()).isEqualTo("BOOKING_SLOT_CONFLICT");
        assertThat(record.responseHttpStatus()).isEqualTo(409);
        assertThat(record.responseBody()).isNotBlank();
        assertThat(record.completedAt()).isNotNull();
    }

    @Test
    void doesNotExecuteCreationWhenTheSameHashIsAlreadyProcessing() {
        CreateBookingCommand command = command(ROOM_ONE_ID, "处理中");
        idempotencyClaimService.claim(USER_ONE_ID, "key-processing", createBookingRequestHasher.hash(command));

        CreateBookingCoordinationResult result = createBookingCoordinator.create(command, "key-processing");

        assertThat(result.status()).isEqualTo(CreateBookingCoordinationStatus.PROCESSING);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking", Integer.class)).isZero();
    }

    @Test
    void allowsDifferentUsersToUseTheSameKeyForDifferentResources() {
        CreateBookingCoordinationResult first = createBookingCoordinator.create(command(ROOM_ONE_ID, "用户一"), "shared-key");
        currentUserProvider.setCurrentUser(userTwo());
        CreateBookingCoordinationResult second = createBookingCoordinator.create(command(ROOM_TWO_ID, "用户二"), "shared-key");

        assertThat(first.status()).isEqualTo(CreateBookingCoordinationStatus.FIRST_SUCCESS);
        assertThat(second.status()).isEqualTo(CreateBookingCoordinationStatus.FIRST_SUCCESS);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key = 'shared-key'", Integer.class)).isEqualTo(2);
    }

    @Test
    void allowsOnlyOneConcurrentCreateForTheSameUserAndKey() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<CreateBookingCoordinationStatus>> results = List.of(
                    executor.submit(() -> createWhenStarted(ready, start)),
                    executor.submit(() -> createWhenStarted(ready, start)));
            ready.await();
            start.countDown();

            List<CreateBookingCoordinationStatus> outcomes = List.of(results.get(0).get(), results.get(1).get());
            assertThat(outcomes).contains(CreateBookingCoordinationStatus.FIRST_SUCCESS);
            assertThat(outcomes).allMatch(status -> status == CreateBookingCoordinationStatus.FIRST_SUCCESS
                    || status == CreateBookingCoordinationStatus.REPLAY_SUCCESS
                    || status == CreateBookingCoordinationStatus.PROCESSING);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking WHERE room_id = ?", Integer.class, ROOM_ONE_ID)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key = 'key-concurrent'", Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private CreateBookingCoordinationStatus createWhenStarted(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return createBookingCoordinator.create(command(ROOM_ONE_ID, "并发同键"), "key-concurrent").status();
    }

    private CreateBookingCommand command(long roomId, String subject) {
        return new CreateBookingCommand(
                roomId,
                subject,
                LocalDateTime.of(2026, 8, 22, 11, 0),
                LocalDateTime.of(2026, 8, 22, 12, 0),
                5,
                "张三\n李四",
                "会议说明");
    }

    private CurrentUser userOne() {
        return new CurrentUser(USER_ONE_ID, "幂等用户一", "USER", "ACTIVE", "测试部门");
    }

    private CurrentUser userTwo() {
        return new CurrentUser(USER_TWO_ID, "幂等用户二", "USER", "ACTIVE", "测试部门");
    }

    private void removeFixture() {
        jdbcTemplate.update("DELETE FROM idempotency_record WHERE user_id IN (?, ?)", USER_ONE_ID, USER_TWO_ID);
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE booking_id IN (SELECT id FROM booking WHERE room_id IN (?, ?))", ROOM_ONE_ID, ROOM_TWO_ID);
        jdbcTemplate.update("DELETE FROM booking_slot WHERE room_id IN (?, ?)", ROOM_ONE_ID, ROOM_TWO_ID);
        jdbcTemplate.update("DELETE FROM booking WHERE room_id IN (?, ?)", ROOM_ONE_ID, ROOM_TWO_ID);
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id IN (?, ?)", ROOM_ONE_ID, ROOM_TWO_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", USER_ONE_ID, USER_TWO_ID);
    }

    static class TestConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(Instant.parse("2026-08-22T02:15:00Z"), ZoneId.of("Asia/Shanghai"));
        }

        @Bean
        @Primary
        TestCurrentUserProvider testCurrentUserProvider() {
            return new TestCurrentUserProvider();
        }
    }

    static class TestCurrentUserProvider implements CurrentUserProvider {

        private volatile CurrentUser currentUser;

        @Override
        public CurrentUser currentUser() {
            return currentUser;
        }

        void setCurrentUser(CurrentUser currentUser) {
            this.currentUser = currentUser;
        }
    }
}
