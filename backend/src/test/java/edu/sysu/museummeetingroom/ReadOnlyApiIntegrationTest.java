package edu.sysu.museummeetingroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "local.current-user-id=900001")
@Import(ReadOnlyApiIntegrationTest.FixedClockConfiguration.class)
@Transactional
class ReadOnlyApiIntegrationTest {
    private static final String DAY = "2026-08-22";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ReadOnlyApiIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, department_name, role_code, status)
                VALUES (900001, 'TEST', 'test-user-900001', 'reader', '测试普通用户', '校史馆', 'USER', 'ACTIVE'),
                       (900002, 'TEST', 'test-user-900002', 'other-reader', '另一普通用户', '校史馆', 'USER', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO meeting_room(id, name, location, capacity, facilities_text, usage_notice, status, sort_order)
                VALUES (910001, '测试停用会议室', '一层', 10, NULL, NULL, 'DISABLED', 1),
                       (910002, '测试启用会议室', '二层', 20, '屏幕', '测试须知', 'ENABLED', 2)
                """);
        insertBooking(920001, "未来会议", "2026-08-22 11:00:00", "2026-08-22 12:00:00", "ACTIVE");
        insertBooking(920002, "进行中会议", "2026-08-22 10:00:00", "2026-08-22 10:30:00", "ACTIVE");
        insertBooking(920003, "已结束会议", "2026-08-22 09:00:00", "2026-08-22 10:00:00", "ACTIVE");
        insertBooking(920004, "已取消会议", "2026-08-22 13:00:00", "2026-08-22 13:30:00", "CANCELLED");
        insertOtherUserBooking();
        jdbcTemplate.update("""
                INSERT INTO booking_slot(id, booking_id, room_id, slot_start, occupancy_state)
                VALUES (930001, 920004, 910002, '2026-08-22 10:00:00', 'CANCELLED_CURRENT_SLOT_HOLD')
                """);
    }

    @Test
    void meUsesDatabaseBackedLocalCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("X-Request-Id", "safe.request-id:1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "safe.request-id:1"))
                .andExpect(jsonPath("$.id").value(900001))
                .andExpect(jsonPath("$.displayName").value("测试普通用户"))
                .andExpect(jsonPath("$.roleCode").value("USER"));
    }

    @Test
    void roomsContainBothStatusesAndUseConfiguredOrdering() throws Exception {
        mockMvc.perform(get("/api/v1/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(910001))
                .andExpect(jsonPath("$[0].status").value("DISABLED"))
                .andExpect(jsonPath("$[1].id").value(910002))
                .andExpect(jsonPath("$[1].status").value("ENABLED"));
    }

    @Test
    void scheduleAppliesBoundaryDisplayAndPrivacyRules() throws Exception {
        mockMvc.perform(get("/api/v1/schedules").param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.slotMinutes").value(30))
                .andExpect(jsonPath("$.bookings.length()").value(4))
                .andExpect(jsonPath("$.bookings[?(@.subject == '未来会议')].displayStatus").value("UPCOMING"))
                .andExpect(jsonPath("$.bookings[?(@.subject == '进行中会议')].displayStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.bookings[?(@.subject == '已结束会议')].displayStatus").value("ENDED"))
                .andExpect(jsonPath("$.bookings[?(@.subject == '未来会议')].isMine").value(true))
                .andExpect(jsonPath("$.bookings[?(@.subject == '其他用户会议')].isMine").value(false))
                .andExpect(jsonPath("$.bookings[?(@.subject == '已取消会议')]").isEmpty())
                .andExpect(jsonPath("$.bookings[0].participantsText").doesNotExist())
                .andExpect(jsonPath("$.bookings[0].description").doesNotExist())
                .andExpect(jsonPath("$.bookings[0].cancelReason").doesNotExist())
                .andExpect(jsonPath("$.unavailableSlots[0].reason").value("CANCELLED_CURRENT_SLOT_HOLD"));
    }

    @Test
    void scheduleDateRangeAllowsHistoryTodayAndThirteenDaysButRejectsFourteenDays() throws Exception {
        mockMvc.perform(get("/api/v1/schedules").param("date", "2020-01-01")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/schedules").param("date", DAY)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/schedules").param("date", "2026-09-04")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/schedules").param("date", "2026-09-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
    }

    @Test
    void invalidRequestIdIsReplacedAndNeverReflected() throws Exception {
        String responseRequestId = mockMvc.perform(get("/api/v1/rooms").header("X-Request-Id", "bad\r\nvalue"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(responseRequestId).matches("[A-Za-z0-9._:-]{1,64}").isNotEqualTo("bad\r\nvalue");
    }

    @Test
    void bookingDetailUsesOwnerAdminAndPrivacyBoundaries() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/920001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingNo").value("TEST-920001"))
                .andExpect(jsonPath("$.room.id").value(910002))
                .andExpect(jsonPath("$.organizer.id").value(900001))
                .andExpect(jsonPath("$.participantsText").value("私密参会人员"))
                .andExpect(jsonPath("$.description").value("私密会议说明"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.displayStatus").value("UPCOMING"));
        mockMvc.perform(get("/api/v1/bookings/920002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayStatus").value("IN_PROGRESS"));
        mockMvc.perform(get("/api/v1/bookings/920003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayStatus").value("ENDED"));
        mockMvc.perform(get("/api/v1/bookings/920004"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.displayStatus").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/bookings/920005"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_ACCESS_DENIED"))
                .andExpect(jsonPath("$.participantsText").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist());

    }

    @Test
    void bookingDetailReturnsConsistentNotFoundAndPathValidationErrors() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/bookings/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
    }

    @Test
    void myBookingsReturnsOnlyCurrentUsersHistoryWithFixedPagination() throws Exception {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking_slot WHERE booking_id = 920003", Integer.class)).isZero();

        mockMvc.perform(get("/api/v1/me/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].id").value(920004))
                .andExpect(jsonPath("$.items[0].displayStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.items[1].displayStatus").value("UPCOMING"))
                .andExpect(jsonPath("$.items[2].displayStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.items[3].displayStatus").value("ENDED"))
                .andExpect(jsonPath("$.items[?(@.id == 920005)]").isEmpty());

        mockMvc.perform(get("/api/v1/me/bookings").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(920002));
        mockMvc.perform(get("/api/v1/me/bookings").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/me/bookings").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/me/bookings").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
    }

    @Test
    void myBookingsFiltersByDerivedStatusAndDateBeforePagination() throws Exception {
        mockMvc.perform(get("/api/v1/me/bookings")
                        .param("status", "UPCOMING")
                        .param("date", DAY)
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].id").value(920001));
        mockMvc.perform(get("/api/v1/me/bookings").param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(920002));
        mockMvc.perform(get("/api/v1/me/bookings").param("status", "ENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(920003));
        mockMvc.perform(get("/api/v1/me/bookings").param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(920004));
        mockMvc.perform(get("/api/v1/me/bookings").param("status", "ACTIVE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
    }

    private void insertBooking(long id, String subject, String start, String end, String status) {
        jdbcTemplate.update("""
                INSERT INTO booking(id, booking_no, room_id, organizer_user_id, organizer_name_snapshot, subject,
                    participants_text, description, start_time, end_time, status, version)
                VALUES (?, ?, 910002, 900001, '测试普通用户', ?, '私密参会人员', '私密会议说明', ?, ?, ?, 1)
                """, id, "TEST-" + id, subject, start, end, status);
    }

    private void insertOtherUserBooking() {
        jdbcTemplate.update("""
                INSERT INTO booking(id, booking_no, room_id, organizer_user_id, organizer_name_snapshot, subject,
                    participants_text, description, start_time, end_time, status, version)
                VALUES (920005, 'TEST-920005', 910002, 900002, '另一普通用户', '其他用户会议',
                    '其他用户私密参会人员', '其他用户私密会议说明',
                    '2026-08-22 14:00:00', '2026-08-22 14:30:00', 'ACTIVE', 1)
                """);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(Instant.parse("2026-08-22T02:15:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
