package edu.sysu.museummeetingroom.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "local.current-user-id=987001")
@Import(AdminBookingCancelIntegrationTest.FixedClockConfiguration.class)
class AdminBookingCancelIntegrationTest {
    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    @Autowired
    AdminBookingCancelIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }
    @BeforeEach void setUp() {
        clean();
        jdbcTemplate.update("INSERT INTO sys_user(id,auth_provider,external_subject,login_name,display_name,role_code,status) VALUES (987001,'TEST','admin-cancel','admin-cancel','管理员','ADMIN','ACTIVE'),(987002,'TEST','owner-cancel','owner-cancel','预约人','USER','ACTIVE')");
        jdbcTemplate.update("INSERT INTO meeting_room(id,name,location,capacity,status,sort_order) VALUES (987010,'D3测试室','测试',10,'ENABLED',1)");
        jdbcTemplate.update("INSERT INTO booking(id,booking_no,room_id,organizer_user_id,organizer_name_snapshot,subject,start_time,end_time,status,version) VALUES (987101,'D3-CANCEL',987010,987002,'预约人','原主题','2026-08-22 11:00:00','2026-08-22 12:00:00','ACTIVE',1)");
        jdbcTemplate.update("INSERT INTO booking_slot(booking_id,room_id,slot_start,occupancy_state) VALUES (987101,987010,'2026-08-22 11:00:00','ACTIVE'),(987101,987010,'2026-08-22 11:30:00','ACTIVE')");
    }
    @AfterEach void tearDown() { clean(); }
    @Test void cancelsOthersUpcomingBookingAndPersistsAdminReasonAudit() throws Exception {
        mockMvc.perform(post("/api/v1/admin/bookings/987101/cancel").contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"reason\":\"  管理员取消  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.slotRelease.mode").value("IMMEDIATE"));
        assertThat(jdbcTemplate.queryForObject("SELECT cancel_reason FROM booking WHERE id=987101", String.class)).isEqualTo("管理员取消");
        assertThat(jdbcTemplate.queryForObject("SELECT reason FROM booking_audit_log WHERE booking_id=987101", String.class)).isEqualTo("管理员取消");
        assertThat(jdbcTemplate.queryForObject("SELECT actor_role_snapshot FROM booking_audit_log WHERE booking_id=987101", String.class)).isEqualTo("ADMIN");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_slot WHERE booking_id=987101", Integer.class)).isZero();
    }
    private void clean() {
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE booking_id=987101");
        jdbcTemplate.update("DELETE FROM booking_slot WHERE booking_id=987101");
        jdbcTemplate.update("DELETE FROM booking WHERE id=987101");
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id=987010");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (987001,987002)");
    }
    static class FixedClockConfiguration { @Bean @Primary Clock fixedBusinessClock() { return Clock.fixed(Instant.parse("2026-08-22T02:12:00Z"), ZoneId.of("Asia/Shanghai")); } }
}
