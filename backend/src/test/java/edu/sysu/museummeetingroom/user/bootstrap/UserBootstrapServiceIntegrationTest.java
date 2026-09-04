package edu.sysu.museummeetingroom.user.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.sysu.museummeetingroom.bootstrap.SchemaVerificationRunner;
import edu.sysu.museummeetingroom.maintenance.MaintenanceScheduler;
import edu.sysu.museummeetingroom.maintenance.MaintenanceSchedulingConfiguration;
import edu.sysu.museummeetingroom.user.mapper.UserMaintenanceMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("user-bootstrap")
@TestPropertySource(properties = "user-bootstrap.enabled=false")
class UserBootstrapServiceIntegrationTest {

    private final UserBootstrapService userBootstrapService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationContext applicationContext;

    @MockBean
    private UserMaintenanceMapper userMaintenanceMapper;

    @Autowired
    UserBootstrapServiceIntegrationTest(
            UserBootstrapService userBootstrapService,
            PasswordEncoder passwordEncoder,
            ApplicationContext applicationContext) {
        this.userBootstrapService = userBootstrapService;
        this.passwordEncoder = passwordEncoder;
        this.applicationContext = applicationContext;
    }

    @BeforeEach
    void setUp() {
        when(userMaintenanceMapper.countAll()).thenReturn(0);
    }

    @Test
    void createsThirtyEightActiveUsersAndThreeDisabledLegacyUsersWithBcryptPins() {
        List<InsertedUser> insertedUsers = new ArrayList<>();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            insertedUsers.add(new InsertedUser(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2),
                    invocation.getArgument(3),
                    invocation.getArgument(4),
                    invocation.getArgument(5),
                    invocation.getArgument(6)));
            return 1;
        }).when(userMaintenanceMapper).insertWithStatus(
                anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString());

        List<UserBootstrapAccount> accounts = accounts();
        userBootstrapService.bootstrap(accounts);

        assertThat(insertedUsers).hasSize(41);
        assertThat(insertedUsers).filteredOn(user -> "ACTIVE".equals(user.status())).hasSize(38);
        assertThat(insertedUsers).filteredOn(user -> "DISABLED".equals(user.status())).hasSize(3);
        assertThat(insertedUsers).filteredOn(user -> "ADMIN".equals(user.roleCode())).hasSize(1);

        InsertedUser firstAccount = insertedUsers.getFirst();
        assertThat(firstAccount.authProvider()).isEqualTo("PIN_TRIAL");
        assertThat(firstAccount.externalSubject()).isNotBlank();
        assertThat(firstAccount.loginName()).isEqualTo("pin-" + firstAccount.externalSubject());
        assertThat(firstAccount.pinHash()).isNotEqualTo("0123");
        assertThat(passwordEncoder.matches("0123", firstAccount.pinHash())).isTrue();

        assertThat(insertedUsers)
                .filteredOn(user -> UserBootstrapLegacyUsers.DISPLAY_NAME_SET.contains(user.displayName()))
                .allSatisfy(user -> {
                    assertThat(user.roleCode()).isEqualTo("USER");
                    assertThat(user.status()).isEqualTo("DISABLED");
                    assertThat(user.pinHash()).isNull();
                });
    }

    @Test
    void rejectsNonEmptySysUserWithoutWritingAnyRows() {
        when(userMaintenanceMapper.countAll()).thenReturn(1);

        assertThatThrownBy(() -> userBootstrapService.bootstrap(accounts()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("user-bootstrap 只能在空 sys_user 上执行。");

        verify(userMaintenanceMapper, never()).insertWithStatus(
                anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void propagatesInsertFailureToTheTransactionalBoundary() {
        doThrow(new IllegalStateException("insert failure"))
                .when(userMaintenanceMapper)
                .insertWithStatus(anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString());

        assertThatThrownBy(() -> userBootstrapService.bootstrap(accounts()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("insert failure");
    }

    @Test
    void bootstrapProfileExcludesNormalStartupRunnerAndScheduler() {
        assertThat(applicationContext.getBeansOfType(SchemaVerificationRunner.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(MaintenanceSchedulingConfiguration.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(MaintenanceScheduler.class)).isEmpty();
    }

    private List<UserBootstrapAccount> accounts() {
        List<UserBootstrapAccount> accounts = new ArrayList<>();
        for (int index = 1; index <= 38; index++) {
            accounts.add(new UserBootstrapAccount(
                    "初始化测试用户%02d".formatted(index),
                    index == 1 ? "ADMIN" : "USER",
                    "%04d".formatted(122 + index),
                    index));
        }
        accounts.set(0, new UserBootstrapAccount("初始化测试用户01", "ADMIN", "0123", 1));
        return accounts;
    }

    private record InsertedUser(
            String authProvider,
            String externalSubject,
            String loginName,
            String displayName,
            String pinHash,
            String roleCode,
            String status) {
    }
}
