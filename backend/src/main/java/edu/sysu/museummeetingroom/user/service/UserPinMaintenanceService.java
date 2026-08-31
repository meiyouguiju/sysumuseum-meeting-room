package edu.sysu.museummeetingroom.user.service;

import edu.sysu.museummeetingroom.user.mapper.SysUserMapper;
import edu.sysu.museummeetingroom.user.mapper.UserMaintenanceMapper;
import edu.sysu.museummeetingroom.user.mapper.UserRow;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("pin-maintenance")
@RequiredArgsConstructor
public class UserPinMaintenanceService {

    private final SysUserMapper sysUserMapper;
    private final UserMaintenanceMapper userMaintenanceMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void execute(UserPinMaintenanceCommand command) {
        switch (command.action()) {
            case "create" -> create(command);
            case "set-pin" -> setPin(command);
            case "enable" -> enable(command);
            case "disable" -> disable(command);
            default -> throw new IllegalArgumentException("user-maintenance.action 只能为 create、set-pin、enable 或 disable。");
        }
    }

    private void create(UserPinMaintenanceCommand command) {
        validatePin(command.pin());
        String displayName = requireName(command.name());
        validateRole(command.roleCode());
        rejectDuplicateActiveNameAndPin(displayName, null, command.pin());
        String unique = UUID.randomUUID().toString();
        userMaintenanceMapper.insert(
                unique,
                "pin-" + unique,
                displayName,
                passwordEncoder.encode(command.pin()),
                command.roleCode());
    }

    private void setPin(UserPinMaintenanceCommand command) {
        validatePin(command.pin());
        UserRow user = requireUser(command.userId());
        rejectDuplicateActiveNameAndPin(user.displayName(), user.id(), command.pin());
        userMaintenanceMapper.updatePin(user.id(), passwordEncoder.encode(command.pin()));
    }

    private void enable(UserPinMaintenanceCommand command) {
        validatePin(command.pin());
        UserRow user = requireUser(command.userId());
        if (!passwordEncoder.matches(command.pin(), user.pinHash())) {
            throw new IllegalArgumentException("启用用户时输入的 PIN 与当前 PIN 不一致。");
        }
        rejectDuplicateActiveNameAndPin(user.displayName(), user.id(), command.pin());
        userMaintenanceMapper.updateStatus(user.id(), "ACTIVE");
    }

    private void disable(UserPinMaintenanceCommand command) {
        requireUser(command.userId());
        userMaintenanceMapper.updateStatus(command.userId(), "DISABLED");
    }

    private void rejectDuplicateActiveNameAndPin(String displayName, Long excludedId, String pin) {
        List<UserRow> conflicts = sysUserMapper.findActiveByDisplayName(displayName).stream()
                .filter(user -> !user.id().equals(excludedId))
                .filter(user -> passwordEncoder.matches(pin, user.pinHash()))
                .toList();
        if (!conflicts.isEmpty()) {
            throw new IllegalArgumentException("同名 ACTIVE 用户不能配置相同的 PIN。");
        }
    }

    private UserRow requireUser(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("user-maintenance.user-id 必须为正整数。");
        }
        UserRow user = sysUserMapper.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在。");
        }
        return user;
    }

    private String requireName(String name) {
        String displayName = name.trim();
        if (displayName.isEmpty() || displayName.length() > 100) {
            throw new IllegalArgumentException("姓名不能为空且不能超过100个字符。");
        }
        return displayName;
    }

    private void validatePin(String pin) {
        if (!pin.matches("\\d{4}")) {
            throw new IllegalArgumentException("PIN 必须为4位数字字符串。");
        }
    }

    private void validateRole(String roleCode) {
        if (!"USER".equals(roleCode) && !"ADMIN".equals(roleCode)) {
            throw new IllegalArgumentException("role-code 只能为 USER 或 ADMIN。");
        }
    }
}
