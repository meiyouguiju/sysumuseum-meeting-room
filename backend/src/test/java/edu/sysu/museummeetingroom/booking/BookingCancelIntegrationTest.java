package edu.sysu.museummeetingroom.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import edu.sysu.museummeetingroom.booking.command.UpdateBookingCommand;
import edu.sysu.museummeetingroom.booking.mapper.BookingAuditLogMapper;
import edu.sysu.museummeetingroom.booking.mutation.service.BookingCancelService;
import edu.sysu.museummeetingroom.booking.mutation.service.BookingUpdateService;
import edu.sysu.museummeetingroom.booking.query.service.BookingQueryService;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import edu.sysu.museummeetingroom.maintenance.SlotCleanupService;
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
import org.springframework.boot.test.mock.mockito.SpyBean;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "local.current-user-id=983001")
@Import(BookingCancelIntegrationTest.MutableClockConfiguration.class)
class BookingCancelIntegrationTest {

    private static final long USER_ID = 983001L;
    private static final long OTHER_USER_ID = 983002L;
    private static final long ROOM_ID = 983010L;
    private static final long UPCOMING_ID = 983101L;
    private static final long IN_PROGRESS_ID = 983102L;
    private static final long CANCELLED_ID = 983103L;
    private static final long ENDED_ID = 983104L;
    private static final long OTHER_ID = 983105L;
    private static final long STALE_ID = 983106L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final MutableClock mutableClock;
    private final BookingCancelService bookingCancelService;
    private final BookingUpdateService bookingUpdateService;
    private final SlotCleanupService slotCleanupService;
    private final BookingQueryService bookingQueryService;

    @SpyBean
    private BookingAuditLogMapper bookingAuditLogMapper;

    @Autowired
    BookingCancelIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate, MutableClock mutableClock,
            BookingCancelService bookingCancelService, BookingUpdateService bookingUpdateService,
            SlotCleanupService slotCleanupService, BookingQueryService bookingQueryService) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.mutableClock = mutableClock;
        this.bookingCancelService = bookingCancelService;
        this.bookingUpdateService = bookingUpdateService;
        this.slotCleanupService = slotCleanupService;
        this.bookingQueryService = bookingQueryService;
    }

    @BeforeEach
    void setUp() {
        mutableClock.set("2026-08-22T10:12:00+08:00");
        clean();
        jdbcTemplate.update("INSERT INTO sys_user(id,auth_provider,external_subject,login_name,display_name,role_code,status) VALUES (983001,'TEST','cancel-owner','cancel-owner','取消人','USER','ACTIVE'),(983002,'TEST','cancel-other','cancel-other','其他人','USER','ACTIVE')");
        jdbcTemplate.update("INSERT INTO meeting_room(id,name,location,capacity,status,sort_order) VALUES (983010,'取消测试室','测试地点',10,'ENABLED',1)");
        insertBooking(UPCOMING_ID, USER_ID, "未开始", "2026-08-22 11:00:00", "2026-08-22 12:30:00", "ACTIVE", 1);
        insertSlots(UPCOMING_ID, "11:00", "11:30", "12:00");
        insertBooking(IN_PROGRESS_ID, USER_ID, "进行中", "2026-08-22 10:00:00", "2026-08-22 12:00:00", "ACTIVE", 1);
        insertSlots(IN_PROGRESS_ID, "10:00", "10:30");
        insertBooking(CANCELLED_ID, USER_ID, "已取消", "2026-08-22 11:00:00", "2026-08-22 11:30:00", "CANCELLED", 2);
        insertBooking(ENDED_ID, USER_ID, "已结束", "2026-08-22 09:00:00", "2026-08-22 10:00:00", "ACTIVE", 1);
        insertSlots(ENDED_ID, "09:00", "09:30");
        insertBooking(OTHER_ID, OTHER_USER_ID, "他人", "2026-08-22 13:00:00", "2026-08-22 13:30:00", "ACTIVE", 1);
        insertSlots(OTHER_ID, "13:00");
        insertBooking(STALE_ID, USER_ID, "过期版本", "2026-08-22 14:00:00", "2026-08-22 14:30:00", "ACTIVE", 2);
        insertSlots(STALE_ID, "14:00");
    }

    @AfterEach
    void tearDown() {
        reset(bookingAuditLogMapper);
        clean();
    }

    @Test
    void cancelsUpcomingBookingAndPersistsCompleteAudit() throws Exception {
        mockMvc.perform(cancel(UPCOMING_ID, "  临时取消  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(UPCOMING_ID))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.cancelledAt").value("2026-08-22T10:12:00"))
                .andExpect(jsonPath("$.slotRelease.mode").value("IMMEDIATE"))
                .andExpect(jsonPath("$.slotRelease.heldSlotStart").doesNotExist())
                .andExpect(jsonPath("$.slotRelease.releasedFrom").doesNotExist());
        assertThat(countSlots(UPCOMING_ID)).isZero();
        assertThat(value("SELECT status FROM booking WHERE id=?", UPCOMING_ID)).isEqualTo("CANCELLED");
        assertThat(value("SELECT cancel_reason FROM booking WHERE id=?", UPCOMING_ID)).isEqualTo("临时取消");
        assertThat(value("SELECT last_modified_at FROM booking WHERE id=?", UPCOMING_ID)).isEqualTo("2026-08-22T10:12");
        assertThat(value("SELECT last_modified_by_user_id FROM booking WHERE id=?", UPCOMING_ID)).isEqualTo(String.valueOf(USER_ID));
        assertThat(value("SELECT operation_type FROM booking_audit_log WHERE booking_id=?", UPCOMING_ID)).isEqualTo("CANCEL");
        assertThat(value("SELECT actor_user_id FROM booking_audit_log WHERE booking_id=?", UPCOMING_ID)).isEqualTo(String.valueOf(USER_ID));
        assertThat(value("SELECT actor_role_snapshot FROM booking_audit_log WHERE booking_id=?", UPCOMING_ID)).isEqualTo("USER");
        assertThat(value("SELECT target_owner_user_id FROM booking_audit_log WHERE booking_id=?", UPCOMING_ID)).isEqualTo(String.valueOf(USER_ID));
        assertThat(value("SELECT version_before FROM booking_audit_log WHERE booking_id=?", UPCOMING_ID)).isEqualTo("1");
        assertThat(value("SELECT version_after FROM booking_audit_log WHERE booking_id=?", UPCOMING_ID)).isEqualTo("2");
        assertThat(value("SELECT JSON_UNQUOTE(before_json->'$.status') FROM booking_audit_log WHERE booking_id=?", UPCOMING_ID)).isEqualTo("ACTIVE");
        assertThat(value("SELECT JSON_UNQUOTE(after_json->'$.cancelReason') FROM booking_audit_log WHERE booking_id=?", UPCOMING_ID)).isEqualTo("临时取消");
        for (String field : java.util.List.of("roomId", "startTime", "endTime")) {
            assertThat(value("SELECT JSON_UNQUOTE(before_json->'$." + field + "') FROM booking_audit_log WHERE booking_id=?", UPCOMING_ID))
                    .isEqualTo(value("SELECT JSON_UNQUOTE(after_json->'$." + field + "') FROM booking_audit_log WHERE booking_id=?", UPCOMING_ID));
        }
        assertThat(value("SELECT JSON_UNQUOTE(slot_change_json->'$.mode') FROM booking_audit_log WHERE booking_id=?", UPCOMING_ID)).isEqualTo("IMMEDIATE");
        mockMvc.perform(get("/api/v1/bookings/{bookingId}", UPCOMING_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelsInProgressBookingAndKeepsOnlyCurrentHold() throws Exception {
        mockMvc.perform(cancel(IN_PROGRESS_ID, "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotRelease.mode").value("AFTER_CURRENT_SLOT"))
                .andExpect(jsonPath("$.slotRelease.heldSlotStart").value("2026-08-22T10:00:00"))
                .andExpect(jsonPath("$.slotRelease.releasedFrom").value("2026-08-22T10:30:00"));
        assertThat(countSlots(IN_PROGRESS_ID)).isEqualTo(1);
        assertThat(value("SELECT occupancy_state FROM booking_slot WHERE booking_id=?", IN_PROGRESS_ID)).isEqualTo("CANCELLED_CURRENT_SLOT_HOLD");
        assertThat(value("SELECT JSON_TYPE(after_json->'$.cancelReason') FROM booking_audit_log WHERE booking_id=?", IN_PROGRESS_ID)).isEqualTo("NULL");
        assertThat(value("SELECT JSON_UNQUOTE(slot_change_json->'$.heldSlotStart') FROM booking_audit_log WHERE booking_id=?", IN_PROGRESS_ID)).isEqualTo("2026-08-22T10:00:00");
    }

    @Test
    void handlesThirtyBoundaryWithMissingPastSlot() throws Exception {
        mutableClock.set("2026-08-22T10:30:00+08:00");
        jdbcTemplate.update("DELETE FROM booking_slot WHERE booking_id=? AND slot_start='2026-08-22 10:00:00'", IN_PROGRESS_ID);
        mockMvc.perform(cancel(IN_PROGRESS_ID, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.slotRelease.heldSlotStart").value("2026-08-22T10:30:00"))
                .andExpect(jsonPath("$.slotRelease.releasedFrom").value("2026-08-22T11:00:00"));
        assertThat(value("SELECT occupancy_state FROM booking_slot WHERE booking_id=?", IN_PROGRESS_ID)).isEqualTo("CANCELLED_CURRENT_SLOT_HOLD");
    }

    @Test
    void rejectsInvalidStateOwnerAndVersionWithoutMutation() throws Exception {
        mockMvc.perform(cancel(CANCELLED_ID, null)).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("BOOKING_ALREADY_CANCELLED"));
        mockMvc.perform(cancel(ENDED_ID, null)).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("BOOKING_ALREADY_ENDED"));
        mockMvc.perform(cancel(OTHER_ID, null)).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("BOOKING_ACCESS_DENIED"));
        mockMvc.perform(cancel(999999L, null)).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("BOOKING_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/bookings/{bookingId}/cancel", STALE_ID).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("BOOKING_VERSION_CONFLICT"));
        assertThat(value("SELECT status FROM booking WHERE id=?", STALE_ID)).isEqualTo("ACTIVE");
        assertThat(countAudit(STALE_ID)).isZero();
    }

    @Test
    void validatesCancelRequestBeforeMutation() throws Exception {
        for (String body : java.util.List.of("{}", "{\"version\":0}", "{\"version\":1,\"reason\":\"" + "a".repeat(501) + "\"}")) {
            mockMvc.perform(post("/api/v1/bookings/{bookingId}/cancel", UPCOMING_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
        }
        mockMvc.perform(post("/api/v1/bookings/{bookingId}/cancel", UPCOMING_ID).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"extra\":true}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(cancel(UPCOMING_ID, "a".repeat(500))).andExpect(status().isOk());
        assertThat(value("SELECT cancel_reason FROM booking WHERE id=?", UPCOMING_ID)).hasSize(500);
    }

    @Test
    void concurrentCancelsAllowExactlyOneAndReturnAlreadyCancelledToTheSecondRequest() throws Exception {
        List<MutationOutcome> outcomes = concurrently(
                () -> bookingCancelService.cancel(UPCOMING_ID, 1, null),
                () -> bookingCancelService.cancel(UPCOMING_ID, 1, null));

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.stream().filter(MutationOutcome::successful)).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> "BOOKING_ALREADY_CANCELLED".equals(outcome.errorCode()))).hasSize(1);
        assertThat(value("SELECT status FROM booking WHERE id=?", UPCOMING_ID)).isEqualTo("CANCELLED");
        assertThat(value("SELECT version FROM booking WHERE id=?", UPCOMING_ID)).isEqualTo("2");
        assertThat(countAuditByType(UPCOMING_ID, "CANCEL")).isEqualTo(1);
        assertThat(value("SELECT COUNT(*) FROM booking WHERE id=? AND version=3", UPCOMING_ID)).isEqualTo("0");
    }

    @Test
    void concurrentUpdateAndCancelAllowExactlyOneMutationAndOneAudit() throws Exception {
        UpdateBookingCommand command = new UpdateBookingCommand(1, ROOM_ID, "PATCH 并发获胜", LocalDateTime.of(2026, 8, 22, 11, 0),
                LocalDateTime.of(2026, 8, 22, 12, 30), 1, null, null);
        List<MutationOutcome> outcomes = concurrently(
                () -> bookingUpdateService.update(UPCOMING_ID, command),
                () -> bookingCancelService.cancel(UPCOMING_ID, 1, null));

        assertThat(outcomes.stream().filter(MutationOutcome::successful)).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful()).map(MutationOutcome::errorCode))
                .allMatch(code -> "BOOKING_ALREADY_CANCELLED".equals(code) || "BOOKING_VERSION_CONFLICT".equals(code));
        assertThat(value("SELECT version FROM booking WHERE id=?", UPCOMING_ID)).isEqualTo("2");
        int updateAudits = countAuditByType(UPCOMING_ID, "UPDATE");
        int cancelAudits = countAuditByType(UPCOMING_ID, "CANCEL");
        assertThat(updateAudits + cancelAudits).isEqualTo(1);
        if (updateAudits == 1) {
            assertThat(value("SELECT status FROM booking WHERE id=?", UPCOMING_ID)).isEqualTo("ACTIVE");
            assertThat(cancelAudits).isZero();
        } else {
            assertThat(value("SELECT status FROM booking WHERE id=?", UPCOMING_ID)).isEqualTo("CANCELLED");
            assertThat(updateAudits).isZero();
        }
    }

    @Test
    void currentSlotHoldRetainsUniqueProtectionWhileReleasedFutureSlotCanBeReused() {
        bookingCancelService.cancel(IN_PROGRESS_ID, 1, null);
        insertBooking(983107L, USER_ID, "重占用", "2026-08-22 10:00:00", "2026-08-22 11:00:00", "ACTIVE", 1);
        assertThatThrownBy(() -> insertSlots(983107L, "10:00"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        insertSlots(983107L, "10:30");
        assertThat(countSlots(983107L)).isEqualTo(1);
    }

    @Test
    void cleanupRemovesCancelledCurrentHoldWithoutChangingCancelledBookingHistory() {
        bookingCancelService.cancel(IN_PROGRESS_ID, 1, null);
        mutableClock.set("2026-08-22T10:30:00+08:00");

        assertThat(slotCleanupService.cleanupPastSlots()).isGreaterThanOrEqualTo(1);
        assertThat(countSlots(IN_PROGRESS_ID)).isZero();
        assertThat(value("SELECT status FROM booking WHERE id=?", IN_PROGRESS_ID)).isEqualTo("CANCELLED");
        assertThat(value("SELECT version FROM booking WHERE id=?", IN_PROGRESS_ID)).isEqualTo("2");
        assertThat(countAuditByType(IN_PROGRESS_ID, "CANCEL")).isEqualTo(1);
        assertThat(bookingQueryService.getBookingDetail(IN_PROGRESS_ID).status()).isEqualTo("CANCELLED");
    }

    @Test
    void rollsBackEveryCancelMutationWhenAuditWritingFails() {
        String beforeLastModifiedAt = value("SELECT last_modified_at FROM booking WHERE id=?", UPCOMING_ID);
        String beforeLastModifiedBy = value("SELECT last_modified_by_user_id FROM booking WHERE id=?", UPCOMING_ID);
        doThrow(new RuntimeException("审计故障注入")).when(bookingAuditLogMapper).insertCancelAudit(any());

        assertThatThrownBy(() -> bookingCancelService.cancel(UPCOMING_ID, 1, "回滚验证"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(value("SELECT status FROM booking WHERE id=?", UPCOMING_ID)).isEqualTo("ACTIVE");
        assertThat(value("SELECT version FROM booking WHERE id=?", UPCOMING_ID)).isEqualTo("1");
        assertThat(value("SELECT cancelled_at FROM booking WHERE id=?", UPCOMING_ID)).isNull();
        assertThat(value("SELECT cancelled_by_user_id FROM booking WHERE id=?", UPCOMING_ID)).isNull();
        assertThat(value("SELECT cancel_reason FROM booking WHERE id=?", UPCOMING_ID)).isNull();
        assertThat(value("SELECT last_modified_at FROM booking WHERE id=?", UPCOMING_ID)).isEqualTo(beforeLastModifiedAt);
        assertThat(value("SELECT last_modified_by_user_id FROM booking WHERE id=?", UPCOMING_ID)).isEqualTo(beforeLastModifiedBy);
        assertThat(countSlots(UPCOMING_ID)).isEqualTo(3);
        assertThat(countAuditByType(UPCOMING_ID, "CANCEL")).isZero();
    }

    private List<MutationOutcome> concurrently(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<MutationOutcome>> futures = List.of(
                    executor.submit(mutationTask(first, ready, start)), executor.submit(mutationTask(second, ready, start)));
            ready.await();
            start.countDown();
            return List.of(futures.get(0).get(), futures.get(1).get());
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<MutationOutcome> mutationTask(Callable<?> action, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                action.call();
                return new MutationOutcome(null);
            } catch (ApiException exception) {
                return new MutationOutcome(exception.errorCode());
            }
        };
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder cancel(long bookingId, String reason) {
        String body = reason == null ? "{\"version\":1}" : "{\"version\":1,\"reason\":\"" + reason + "\"}";
        return post("/api/v1/bookings/{bookingId}/cancel", bookingId).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private void insertBooking(long id, long organizerId, String subject, String start, String end, String status, int version) {
        jdbcTemplate.update("INSERT INTO booking(id,booking_no,room_id,organizer_user_id,organizer_name_snapshot,subject,start_time,end_time,status,version) VALUES (?,?,?,?, '取消人',?,?,?,?,?)", id, "CANCEL-" + id, ROOM_ID, organizerId, subject, start, end, status, version);
    }

    private void insertSlots(long bookingId, String... times) {
        for (String time : times) {
            jdbcTemplate.update("INSERT INTO booking_slot(booking_id,room_id,slot_start,occupancy_state) VALUES (?,?,?,'ACTIVE')", bookingId, ROOM_ID, "2026-08-22 " + time + ":00");
        }
    }

    private String value(String sql, long id) {
        Object result = jdbcTemplate.queryForObject(sql, Object.class, id);
        return result == null ? null : result.toString();
    }

    private int countSlots(long bookingId) { return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_slot WHERE booking_id=?", Integer.class, bookingId); }
    private int countAudit(long bookingId) { return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id=?", Integer.class, bookingId); }
    private int countAuditByType(long bookingId, String operationType) { return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log WHERE booking_id=? AND operation_type=?", Integer.class, bookingId, operationType); }

    private void clean() {
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE booking_id BETWEEN 983101 AND 983107");
        jdbcTemplate.update("DELETE FROM booking_slot WHERE booking_id BETWEEN 983101 AND 983107");
        jdbcTemplate.update("DELETE FROM booking WHERE id BETWEEN 983101 AND 983107");
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id=983010");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (983001,983002)");
    }

    static class MutableClockConfiguration {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
    }
    private record MutationOutcome(String errorCode) { private boolean successful() { return errorCode == null; } }
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>();
        void set(String value) { instant.set(Instant.parse(value)); }
        @Override public ZoneId getZone() { return ZONE; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
