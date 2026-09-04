package edu.sysu.museummeetingroom.user.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserBootstrapFileParserTest {

    private final UserBootstrapFileParser userBootstrapFileParser = new UserBootstrapFileParser();

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesThirtyEightAccountsWithCommentsBlankLinesTabsAndSpaces() throws IOException {
        List<String> lines = validAccountLines();
        lines.add(0, "# 一次性初始化文件");
        lines.add(1, "");
        lines.set(0 + 2, "测试用户01\tADMIN    0123");

        List<UserBootstrapAccount> accounts = userBootstrapFileParser.parse(writeFile(lines));

        assertThat(accounts).hasSize(38);
        assertThat(accounts.getFirst())
                .extracting(UserBootstrapAccount::displayName, UserBootstrapAccount::roleCode, UserBootstrapAccount::pin)
                .containsExactly("测试用户01", "ADMIN", "0123");
        assertThat(accounts).filteredOn(account -> "ADMIN".equals(account.roleCode())).hasSize(1);
    }

    @Test
    void rejectsDuplicateNameAndLegacyName() throws IOException {
        List<String> duplicateLines = validAccountLines();
        duplicateLines.set(1, "测试用户01 USER 1001");

        assertThatThrownBy(() -> userBootstrapFileParser.parse(writeFile(duplicateLines)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("第2行：姓名在输入文件中重复。");

        List<String> legacyLines = validAccountLines();
        legacyLines.set(0, "周绅 USER 1000");

        assertThatThrownBy(() -> userBootstrapFileParser.parse(writeFile(legacyLines)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("第1行：不能包含固定历史离职用户。");
    }

    @Test
    void rejectsInvalidRoleAndInvalidPinsWithoutIncludingPinInMessage() throws IOException {
        assertInvalidLine("测试用户01 MANAGER 1000", "角色只能为 USER 或 ADMIN。");
        assertInvalidLine("测试用户01 USER 123", "PIN 必须为4位数字。");
        assertInvalidLine("测试用户01 USER 12345", "PIN 必须为4位数字。");
        assertInvalidLine("测试用户01 USER abcd", "PIN 必须为4位数字。");
    }

    @Test
    void rejectsMissingAndExtraFields() throws IOException {
        assertInvalidLine("测试用户01 USER", "必须包含姓名、角色和 PIN 三个字段。");
        assertInvalidLine("测试用户01 USER 1000 extra", "必须包含姓名、角色和 PIN 三个字段。");
    }

    @Test
    void rejectsAccountCountsOtherThanThirtyEight() throws IOException {
        List<String> tooFew = validAccountLines();
        tooFew.removeLast();
        assertThatThrownBy(() -> userBootstrapFileParser.parse(writeFile(tooFew)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user-bootstrap 账号数量必须为38，实际为37。");

        List<String> tooMany = validAccountLines();
        tooMany.add("测试用户39 USER 1039");
        assertThatThrownBy(() -> userBootstrapFileParser.parse(writeFile(tooMany)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user-bootstrap 账号数量必须为38，实际为39。");
    }

    private void assertInvalidLine(String replacement, String expectedReason) throws IOException {
        List<String> lines = validAccountLines();
        lines.set(0, replacement);

        assertThatThrownBy(() -> userBootstrapFileParser.parse(writeFile(lines)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("第1行：" + expectedReason);
    }

    private Path writeFile(List<String> lines) throws IOException {
        Path file = temporaryDirectory.resolve("accounts-" + System.nanoTime() + ".txt");
        Files.write(file, lines);
        return file;
    }

    private List<String> validAccountLines() {
        List<String> lines = new ArrayList<>();
        for (int index = 1; index <= 38; index++) {
            lines.add("测试用户%02d USER %04d".formatted(index, 1000 + index));
        }
        return lines;
    }
}
