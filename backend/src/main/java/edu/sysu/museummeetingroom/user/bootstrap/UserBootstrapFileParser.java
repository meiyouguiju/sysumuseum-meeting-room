package edu.sysu.museummeetingroom.user.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("user-bootstrap")
public class UserBootstrapFileParser {

    private static final int REQUIRED_ACCOUNT_COUNT = 38;

    public List<UserBootstrapAccount> parse(Path file) {
        List<String> lines = readLines(file);
        List<UserBootstrapAccount> accounts = new ArrayList<>();
        Set<String> displayNames = new HashSet<>();

        for (int index = 0; index < lines.size(); index++) {
            int lineNumber = index + 1;
            String line = lines.get(index).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] fields = line.split("\\s+");
            if (fields.length != 3) {
                throw invalidLine(lineNumber, "必须包含姓名、角色和 PIN 三个字段。");
            }

            String displayName = fields[0].strip();
            String roleCode = fields[1].strip();
            String pin = fields[2].strip();
            validateAccount(lineNumber, displayName, roleCode, pin, displayNames);
            accounts.add(new UserBootstrapAccount(displayName, roleCode, pin, lineNumber));
        }

        if (accounts.size() != REQUIRED_ACCOUNT_COUNT) {
            throw new IllegalArgumentException(
                    "user-bootstrap 账号数量必须为38，实际为" + accounts.size() + "。");
        }

        return List.copyOf(accounts);
    }

    private List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取 user-bootstrap 输入文件。", exception);
        }
    }

    private void validateAccount(
            int lineNumber,
            String displayName,
            String roleCode,
            String pin,
            Set<String> displayNames) {
        if (displayName.isEmpty() || displayName.length() > 100) {
            throw invalidLine(lineNumber, "姓名不能为空且不能超过100个字符。");
        }
        if (!displayNames.add(displayName)) {
            throw invalidLine(lineNumber, "姓名在输入文件中重复。");
        }
        if (UserBootstrapLegacyUsers.DISPLAY_NAME_SET.contains(displayName)) {
            throw invalidLine(lineNumber, "不能包含固定历史离职用户。");
        }
        if (!"USER".equals(roleCode) && !"ADMIN".equals(roleCode)) {
            throw invalidLine(lineNumber, "角色只能为 USER 或 ADMIN。");
        }
        if (!pin.matches("[0-9]{4}")) {
            throw invalidLine(lineNumber, "PIN 必须为4位数字。");
        }
    }

    private IllegalArgumentException invalidLine(int lineNumber, String reason) {
        return new IllegalArgumentException("第" + lineNumber + "行：" + reason);
    }
}
