package edu.sysu.museummeetingroom.user.bootstrap;

import edu.sysu.museummeetingroom.user.mapper.UserMaintenanceMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("user-bootstrap")
@RequiredArgsConstructor
public class UserBootstrapService {

    private static final String PIN_AUTH_PROVIDER = "PIN_TRIAL";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String DISABLED_STATUS = "DISABLED";
    private static final String USER_ROLE = "USER";

    private final UserMaintenanceMapper userMaintenanceMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void bootstrap(List<UserBootstrapAccount> accounts) {
        if (userMaintenanceMapper.countAll() != 0) {
            throw new IllegalStateException("user-bootstrap 只能在空 sys_user 上执行。");
        }

        for (UserBootstrapAccount account : accounts) {
            insertUser(
                    account.displayName(),
                    passwordEncoder.encode(account.pin()),
                    account.roleCode(),
                    ACTIVE_STATUS);
        }
        for (String displayName : UserBootstrapLegacyUsers.DISPLAY_NAMES) {
            insertUser(displayName, null, USER_ROLE, DISABLED_STATUS);
        }
    }

    private void insertUser(String displayName, String pinHash, String roleCode, String status) {
        String externalSubject = UUID.randomUUID().toString();
        userMaintenanceMapper.insertWithStatus(
                PIN_AUTH_PROVIDER,
                externalSubject,
                "pin-" + externalSubject,
                displayName,
                pinHash,
                roleCode,
                status);
    }
}
