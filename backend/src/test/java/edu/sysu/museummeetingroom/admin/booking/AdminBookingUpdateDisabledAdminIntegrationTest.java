package edu.sysu.museummeetingroom.admin.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "local.current-user-id=986301")
class AdminBookingUpdateDisabledAdminIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AdminBookingUpdateDisabledAdminIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        removeFixture();
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, role_code, status)
                VALUES (986301, 'TEST', 'disabled-admin-update', 'disabled-admin-update', '停用管理员', 'ADMIN', 'DISABLED')
                """);
    }

    @AfterEach
    void tearDown() {
        removeFixture();
    }

    @Test
    void disabledAdminIsUnauthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/bookings/{bookingId}", 123456789L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"subject\":\"主题\",\"attendeeCount\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    private void removeFixture() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = 986301");
    }
}
