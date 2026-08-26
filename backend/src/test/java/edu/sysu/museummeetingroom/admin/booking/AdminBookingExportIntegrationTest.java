package edu.sysu.museummeetingroom.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "local.current-user-id=988001")
@Import(AdminBookingExportIntegrationTest.FixedClockConfiguration.class)
class AdminBookingExportIntegrationTest {

    private static final long ADMIN_ID = 988001L;
    private static final long OWNER_ID = 988002L;
    private static final long ROOM_ID = 988010L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AdminBookingExportIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        removeFixture();
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, role_code, status)
                VALUES (988001, 'TEST', 'export-admin', 'export-admin', '导出管理员', 'ADMIN', 'ACTIVE'),
                       (988002, 'TEST', 'export-owner', 'export-owner', '导出预约人', 'USER', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO meeting_room(id, name, location, capacity, status, sort_order)
                VALUES (988010, '导出测试会议室', '测试地点', 20, 'ENABLED', 1)
                """);
        insertBooking(988101L, "EXPORT-988101", "普通主题", "2026-08-24 11:00:00", "ACTIVE", null);
        insertBooking(988102L, "EXPORT-988102", "=危险,\"主题\"", "2026-08-24 23:59:59", "CANCELLED", "换行\r\n原因");
        insertBooking(988103L, "EXPORT-988103", "范围外主题", "2026-08-25 00:00:00", "ACTIVE", null);
    }

    @AfterEach
    void tearDown() {
        removeFixture();
    }

    @Test
    void exportsAllRecordsWithoutAnImplicitTodayDateLimit() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/bookings/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"booking-records.csv\""))
                .andReturn();

        byte[] content = result.getResponse().getContentAsByteArray();
        assertThat(content).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String csv = new String(content, 3, content.length - 3, StandardCharsets.UTF_8);
        assertThat(csv)
                .startsWith("预约号,会议室,预约人,主题,预计人数,参会人员,说明,开始时间,结束时间,状态,取消时间,取消原因,创建时间,最后修改时间\r\n")
                .contains("EXPORT-988101")
                .contains("\"'=危险,\"\"主题\"\"\"")
                .contains("\"换行\r\n原因\"")
                .contains("EXPORT-988103");
    }

    @Test
    void filtersByInclusiveDateRangeAndRejectsReversedOrMalformedDates() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/bookings/export")
                        .param("fromDate", "2026-08-25")
                        .param("toDate", "2026-08-25"))
                .andExpect(status().isOk())
                .andReturn();
        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("EXPORT-988103").doesNotContain("EXPORT-988101", "EXPORT-988102");

        mockMvc.perform(get("/api/v1/admin/bookings/export")
                        .param("fromDate", "2026-08-25")
                        .param("toDate", "2026-08-24"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/admin/bookings/export").param("fromDate", "2026/08/24"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
    }

    @Test
    void supportsSingleRangeBoundsAndMatchesAdminListFilters() throws Exception {
        MvcResult fromOnly = mockMvc.perform(get("/api/v1/admin/bookings/export")
                        .param("fromDate", "2026-08-25"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(fromOnly.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("EXPORT-988103")
                .doesNotContain("EXPORT-988101", "EXPORT-988102");

        MvcResult toOnly = mockMvc.perform(get("/api/v1/admin/bookings/export")
                        .param("toDate", "2026-08-24"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(toOnly.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("EXPORT-988101", "EXPORT-988102")
                .doesNotContain("EXPORT-988103");

        mockMvc.perform(get("/api/v1/admin/bookings/export")
                        .param("date", "2026-08-24")
                        .param("toDate", "2026-08-24"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
    }

    @Test
    void exportsAllRecordsUsingTheSameV11FiltersAsTheAdminList() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/bookings/export")
                        .param("organizerKeyword", "导出预约人")
                        .param("date", "2026-08-24")
                        .param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andReturn();
        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("EXPORT-988102").doesNotContain("EXPORT-988101", "EXPORT-988103");

        mockMvc.perform(get("/api/v1/admin/bookings/export")
                        .param("date", "2026-08-24")
                        .param("fromDate", "2026-08-24"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
    }

    @Test
    void statusAndOrganizerFiltersExportAllMatchingDatesLikeTheAdminList() throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings")
                        .param("organizerKeyword", "导出预约人")
                        .param("status", "UPCOMING")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[?(@.id == 988101)]").exists())
                .andExpect(jsonPath("$.items[?(@.id == 988103)]").exists());

        MvcResult statusResult = mockMvc.perform(get("/api/v1/admin/bookings/export")
                        .param("status", "UPCOMING"))
                .andExpect(status().isOk())
                .andReturn();
        String statusCsv = statusResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(statusCsv).contains("EXPORT-988101", "EXPORT-988103").doesNotContain("EXPORT-988102");

        MvcResult organizerResult = mockMvc.perform(get("/api/v1/admin/bookings/export")
                        .param("organizerKeyword", "导出预约人"))
                .andExpect(status().isOk())
                .andReturn();
        String organizerCsv = organizerResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(organizerCsv).contains("EXPORT-988101", "EXPORT-988102", "EXPORT-988103");
    }

    private void insertBooking(
            long id, String bookingNo, String subject, String startTime, String status, String cancelReason) {
        jdbcTemplate.update("""
                INSERT INTO booking(id, booking_no, room_id, organizer_user_id, organizer_name_snapshot,
                    subject, attendee_count, participants_text, description, start_time, end_time,
                    status, cancelled_at, cancel_reason, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, '导出预约人', ?, 12, '@参会人员', '说明,含逗号', ?,
                    DATE_ADD(?, INTERVAL 30 MINUTE), ?, IF(? = 'CANCELLED', ?, NULL), ?, 1,
                    '2026-08-20 08:00:00', '2026-08-21 09:00:00')
                """, id, bookingNo, ROOM_ID, OWNER_ID, subject, startTime, startTime,
                status, status, startTime, cancelReason);
    }

    private void removeFixture() {
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE booking_id BETWEEN 988101 AND 988103");
        jdbcTemplate.update("DELETE FROM booking_slot WHERE booking_id BETWEEN 988101 AND 988103");
        jdbcTemplate.update("DELETE FROM booking WHERE id BETWEEN 988101 AND 988103");
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id = ?", ROOM_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", ADMIN_ID, OWNER_ID);
    }

    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(Instant.parse("2026-08-24T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
