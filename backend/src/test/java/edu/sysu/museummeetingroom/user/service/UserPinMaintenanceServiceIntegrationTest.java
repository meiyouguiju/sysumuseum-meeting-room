package edu.sysu.museummeetingroom.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("pin-maintenance")
@TestPropertySource(properties = "user-maintenance.enabled=false")
class UserPinMaintenanceServiceIntegrationTest {

    private static final long UPDATE_USER_ID = 972001L;
    private static final long DUPLICATE_USER_ID = 972002L;
    private static final String CREATED_USER_NAME = "PIN维护创建测试";
    private static final String DUPLICATE_USER_NAME = "PIN维护同名测试";

    private final UserPinMaintenanceService userPinMaintenanceService;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    UserPinMaintenanceServiceIntegrationTest(
            UserPinMaintenanceService userPinMaintenanceService,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.userPinMaintenanceService = userPinMaintenanceService;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
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
    void createCommitsBcryptPinToDatabase() {
        userPinMaintenanceService.execute(new UserPinMaintenanceCommand(
                "create", CREATED_USER_NAME, "0376", 0, "USER"));

        String pinHash = jdbcTemplate.queryForObject(
                "SELECT pin_hash FROM sys_user WHERE display_name = ?", String.class, CREATED_USER_NAME);
        assertThat(pinHash).isNotBlank();
        assertThat(passwordEncoder.matches("0376", pinHash)).isTrue();
    }

    @Test
    void setPinDisableAndEnableEachCommitTheirChanges() {
        insertUser(UPDATE_USER_ID, "PIN维护状态测试", "1111", "ACTIVE");

        userPinMaintenanceService.execute(new UserPinMaintenanceCommand(
                "set-pin", "", "2222", UPDATE_USER_ID, "USER"));
        assertThat(passwordEncoder.matches("2222", pinHash(UPDATE_USER_ID))).isTrue();

        userPinMaintenanceService.execute(new UserPinMaintenanceCommand(
                "disable", "", "", UPDATE_USER_ID, "USER"));
        assertThat(status(UPDATE_USER_ID)).isEqualTo("DISABLED");

        userPinMaintenanceService.execute(new UserPinMaintenanceCommand(
                "enable", "", "2222", UPDATE_USER_ID, "USER"));
        assertThat(status(UPDATE_USER_ID)).isEqualTo("ACTIVE");
    }

    @Test
    void duplicateActiveNameAndPinIsStillRejected() {
        insertUser(DUPLICATE_USER_ID, DUPLICATE_USER_NAME, "6666", "ACTIVE");

        assertThatThrownBy(() -> userPinMaintenanceService.execute(new UserPinMaintenanceCommand(
                        "create", DUPLICATE_USER_NAME, "6666", 0, "USER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("同名 ACTIVE 用户不能配置相同的 PIN。");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE display_name = ?", Integer.class, DUPLICATE_USER_NAME))
                .isEqualTo(1);
    }

    private void insertUser(long id, String displayName, String pin, String status) {
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, pin_hash, role_code, status)
                VALUES (?, 'TEST', ?, ?, ?, ?, 'USER', ?)
                """,
                id,
                "pin-maintenance-" + id,
                "pin-maintenance-" + id,
                displayName,
                passwordEncoder.encode(pin),
                status);
    }

    private String pinHash(long id) {
        return jdbcTemplate.queryForObject("SELECT pin_hash FROM sys_user WHERE id = ?", String.class, id);
    }

    private String status(long id) {
        return jdbcTemplate.queryForObject("SELECT status FROM sys_user WHERE id = ?", String.class, id);
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", UPDATE_USER_ID, DUPLICATE_USER_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE display_name = ?", CREATED_USER_NAME);
    }
}
