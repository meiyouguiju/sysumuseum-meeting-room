package edu.sysu.museummeetingroom.legacyimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyImportWorkbookParserTest {

    private final LegacyImportWorkbookParser legacyImportWorkbookParser = new LegacyImportWorkbookParser();

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesFrozenWorkbookContractAndPreservesHistoricalFacts() throws IOException {
        List<LegacyImportRecord> records = legacyImportWorkbookParser.parse(
                LegacyImportWorkbookTestSupport.writeValidWorkbook(temporaryDirectory.resolve("valid.xlsx")));

        assertThat(records).hasSize(842);
        assertThat(records).filteredOn(record -> "ACTIVE".equals(record.status())).hasSize(840);
        assertThat(records).filteredOn(record -> "CANCELLED".equals(record.status())).hasSize(2);
        assertThat(records).extracting(LegacyImportRecord::bookingNo).doesNotHaveDuplicates();

        LegacyImportRecord nonAligned = find(records, "LEGACY-2026-000002");
        assertThat(nonAligned.startTime().toLocalTime().toString()).isEqualTo("14:20");
        assertThat(nonAligned.endTime().toLocalTime().toString()).isEqualTo("14:50");
        assertThat(nonAligned.sourceExcelRow()).isEqualTo(2);

        LegacyImportRecord longMeeting = find(records, "LEGACY-2025-000002");
        assertThat(longMeeting.endTime().minusHours(6).minusMinutes(30)).isEqualTo(longMeeting.startTime());

        LegacyImportRecord nullableFields = find(records, "LEGACY-2026-000004");
        assertThat(nullableFields.attendeeCount()).isNull();
        assertThat(nullableFields.participantsText()).isNull();
        assertThat(nullableFields.description()).isNull();
    }

    @Test
    void rejectsMissingOrUnknownSheets() throws IOException {
        Map<String, Integer> missingSheet = new LinkedHashMap<>(LegacyImportWorkbookTestSupport.expectedSheets());
        missingSheet.remove("2024");
        assertThatThrownBy(() -> legacyImportWorkbookParser.parse(
                        LegacyImportWorkbookTestSupport.writeWorkbookWithSheets(temporaryDirectory.resolve("missing.xlsx"), missingSheet)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Excel Sheet 必须且只能为 2026年、2025、2024。");

        Map<String, Integer> unknownSheet = new LinkedHashMap<>(LegacyImportWorkbookTestSupport.expectedSheets());
        unknownSheet.put("未知Sheet", 1);
        assertThatThrownBy(() -> legacyImportWorkbookParser.parse(
                        LegacyImportWorkbookTestSupport.writeWorkbookWithSheets(temporaryDirectory.resolve("unknown.xlsx"), unknownSheet)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Excel Sheet 必须且只能为 2026年、2025、2024。");
    }

    @Test
    void rejectsChangedHeaderAndIncorrectRecordCount() throws IOException {
        Path changedHeader = LegacyImportWorkbookTestSupport.writeValidWorkbook(temporaryDirectory.resolve("header.xlsx"));
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(changedHeader))) {
            workbook.getSheet("2026年").getRow(0).getCell(1).setCellValue("错误列名");
            workbook.write(Files.newOutputStream(changedHeader));
        }
        assertThatThrownBy(() -> legacyImportWorkbookParser.parse(changedHeader))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("2026年 Sheet 第2列必须为“会议主题”。");

        Map<String, Integer> incorrectCount = new LinkedHashMap<>(LegacyImportWorkbookTestSupport.expectedSheets());
        incorrectCount.put("2026年", 167);
        assertThatThrownBy(() -> legacyImportWorkbookParser.parse(
                        LegacyImportWorkbookTestSupport.writeWorkbookWithSheets(temporaryDirectory.resolve("count.xlsx"), incorrectCount)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("2026年 Sheet 记录数必须为168，实际为167。");
    }

    private LegacyImportRecord find(List<LegacyImportRecord> records, String bookingNo) {
        return records.stream()
                .filter(record -> bookingNo.equals(record.bookingNo()))
                .findFirst()
                .orElseThrow();
    }
}
