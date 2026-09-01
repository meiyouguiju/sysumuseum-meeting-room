package edu.sysu.museummeetingroom.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("auth-integration")
@TestPropertySource(properties = "server.forward-headers-strategy=framework")
class SessionAuthenticationHttpIntegrationTest {

    private static final long FIRST_USER_ID = 970001L;
    private static final long SECOND_USER_ID = 970002L;
    private static final long LEADING_ZERO_USER_ID = 970003L;
    private static final int SESSION_MAX_AGE_SECONDS = 60 * 60 * 24 * 30;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationContext applicationContext;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    SessionAuthenticationHttpIntegrationTest(
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate,
            ApplicationContext applicationContext) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.applicationContext = applicationContext;
    }

    @BeforeEach
    void setUp() {
        cleanUsers();
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, pin_hash, role_code, status)
                VALUES (?, 'TEST', 'auth-a', 'auth-a', '张伟', ?, 'USER', 'ACTIVE'),
                       (?, 'TEST', 'auth-b', 'auth-b', '张伟', ?, 'USER', 'ACTIVE'),
                       (?, 'TEST', 'auth-zero', 'auth-zero', '前导零用户', ?, 'ADMIN', 'ACTIVE')
                """,
                FIRST_USER_ID,
                passwordEncoder.encode("1357"),
                SECOND_USER_ID,
                passwordEncoder.encode("4826"),
                LEADING_ZERO_USER_ID,
                passwordEncoder.encode("0376"));
    }

    @AfterEach
    void tearDown() {
        cleanUsers();
    }

    @Test
    void lanLoginCreatesPersistentCookieAndLogoutInvalidatesIt() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        int sessionsBeforeLogin = sessionCount();

        jakarta.servlet.http.Cookie sessionCookie = login("前导零用户", "0376", false);

        assertThat(sessionCookie.getMaxAge()).isEqualTo(SESSION_MAX_AGE_SECONDS);
        assertThat(sessionCookie.getSecure()).isFalse();
        assertThat(sessionCookie.isHttpOnly()).isTrue();
        assertThat(sessionCount()).isGreaterThan(sessionsBeforeLogin);
        mockMvc.perform(get("/api/v1/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(LEADING_ZERO_USER_ID))
                .andExpect(jsonPath("$.roleCode").value("ADMIN"));

        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout").cookie(sessionCookie))
                .andExpect(status().isNoContent())
                .andReturn();
        jakarta.servlet.http.Cookie clearedCookie = logoutResult.getResponse().getCookie("MUSEUM_SESSION");
        assertThat(clearedCookie).isNotNull();
        assertThat(clearedCookie.getMaxAge()).isZero();
        assertThat(clearedCookie.getSecure()).isFalse();
        mockMvc.perform(get("/api/v1/me").cookie(sessionCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void doesNotCreateSpringBootDefaultInMemoryUserDetailsManager() {
        assertThat(applicationContext.containsBean("inMemoryUserDetailsManager")).isFalse();
        assertThat(applicationContext.getBeansOfType(InMemoryUserDetailsManager.class)).isEmpty();
    }

    @Test
    void forwardedHttpsLoginUsesSecureCookieAndSameNamePinsKeepUsersSeparate() throws Exception {
        jakarta.servlet.http.Cookie firstCookie = login("张伟", "1357", true);
        assertThat(firstCookie.getSecure()).isTrue();
        mockMvc.perform(get("/api/v1/me").cookie(firstCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(FIRST_USER_ID));

        jakarta.servlet.http.Cookie secondCookie = login("张伟", "4826", true);
        mockMvc.perform(get("/api/v1/me").cookie(secondCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SECOND_USER_ID));

        mockMvc.perform(post("/api/v1/auth/logout").cookie(firstCookie).header("X-Forwarded-Proto", "https"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/logout").cookie(secondCookie).header("X-Forwarded-Proto", "https"))
                .andExpect(status().isNoContent());
    }

    private jakarta.servlet.http.Cookie login(String name, String pin, boolean forwardedHttps) throws Exception {
        var request = post("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"pin\":\"" + pin + "\"}");
        if (forwardedHttps) {
            request.header("X-Forwarded-Proto", "https");
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        jakarta.servlet.http.Cookie sessionCookie = result.getResponse().getCookie("MUSEUM_SESSION");
        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
    }

    private int sessionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class);
    }

    private void cleanUsers() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?, ?)",
                FIRST_USER_ID,
                SECOND_USER_ID,
                LEADING_ZERO_USER_ID);
    }
}
