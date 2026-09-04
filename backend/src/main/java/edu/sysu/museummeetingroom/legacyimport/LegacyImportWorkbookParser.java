package edu.sysu.museummeetingroom.legacyimport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("legacy-import")
public class LegacyImportWorkbookParser {

    private static final List<String> EXPECTED_HEADERS = List.of(
            "会议日期", "会议主题", "会议室名称", "预订人", "参会人数", "会议状态",
            "开始时间", "结束时间", "参会人员", "说明", "原Excel行号");
    private static final Map<String, SheetDefinition> EXPECTED_SHEETS = Map.of(
            "2026年", new SheetDefinition("2026", 168),
            "2025", new SheetDefinition("2025", 235),
            "2024", new SheetDefinition("2024", 439));
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public List<LegacyImportRecord> parse(Path file) {
        try (InputStream inputStream = Files.newInputStream(file);
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            validateSheetNames(workbook);
            List<LegacyImportRecord> records = new ArrayList<>();
            Set<String> bookingNumbers = new LinkedHashSet<>();

            for (Map.Entry<String, SheetDefinition> entry : EXPECTED_SHEETS.entrySet()) {
                List<LegacyImportRecord> sheetRecords = parseSheet(workbook.getSheet(entry.getKey()), entry.getKey(), entry.getValue());
                if (sheetRecords.size() != entry.getValue().expectedRecordCount()) {
                    throw new IllegalArgumentException(
                            entry.getKey() + " Sheet 记录数必须为" + entry.getValue().expectedRecordCount()
                                    + "，实际为" + sheetRecords.size() + "。");
                }
                for (LegacyImportRecord record : sheetRecords) {
                    if (!bookingNumbers.add(record.bookingNo())) {
                        throw invalidRecord(record.sheetName(), record.sourceExcelRow(), "booking_no 重复。");
                    }
                }
                records.addAll(sheetRecords);
            }

            if (records.size() != 842) {
                throw new IllegalArgumentException("历史预约总数必须为842，实际为" + records.size() + "。");
            }
            return List.copyOf(records);
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取 legacy-import Excel 文件。", exception);
        }
    }

    private void validateSheetNames(Workbook workbook) {
        Set<String> actualSheetNames = new LinkedHashSet<>();
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            actualSheetNames.add(workbook.getSheetName(index));
        }
        if (!actualSheetNames.equals(EXPECTED_SHEETS.keySet())) {
            throw new IllegalArgumentException("Excel Sheet 必须且只能为 2026年、2025、2024。");
        }
    }

    private List<LegacyImportRecord> parseSheet(Sheet sheet, String sheetName, SheetDefinition definition) {
        validateHeaders(sheet, sheetName);
        List<LegacyImportRecord> records = new ArrayList<>();

        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isBlankRow(row)) {
                continue;
            }
            records.add(parseRecord(row, sheetName, definition.year()));
        }
        return records;
    }

    private void validateHeaders(Sheet sheet, String sheetName) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null || headerRow.getLastCellNum() != EXPECTED_HEADERS.size()) {
            throw new IllegalArgumentException(sheetName + " Sheet 列契约不匹配。");
        }
        for (int index = 0; index < EXPECTED_HEADERS.size(); index++) {
            String actualHeader = requiredString(headerRow.getCell(index), sheetName, "表头", "列名");
            if (!EXPECTED_HEADERS.get(index).equals(actualHeader)) {
                throw new IllegalArgumentException(sheetName + " Sheet 第" + (index + 1) + "列必须为“"
                        + EXPECTED_HEADERS.get(index) + "”。");
            }
        }
    }

    private LegacyImportRecord parseRecord(Row row, String sheetName, String year) {
        int sourceExcelRow = readPositiveInteger(row.getCell(10), sheetName, "未知", "原Excel行号");
        LocalDate meetingDate = readDate(row.getCell(0), sheetName, sourceExcelRow, "会议日期");
        String subject = requiredString(row.getCell(1), sheetName, sourceExcelRow, "会议主题");
        String roomName = requiredString(row.getCell(2), sheetName, sourceExcelRow, "会议室名称");
        String organizerName = requiredString(row.getCell(3), sheetName, sourceExcelRow, "预订人");
        Integer attendeeCount = optionalAttendeeCount(row.getCell(4), sheetName, sourceExcelRow);
        String status = readStatus(row.getCell(5), sheetName, sourceExcelRow);
        LocalTime startTime = readTime(row.getCell(6), sheetName, sourceExcelRow, "开始时间");
        LocalTime endTime = readTime(row.getCell(7), sheetName, sourceExcelRow, "结束时间");
        String participantsText = optionalString(row.getCell(8), sheetName, sourceExcelRow, "参会人员");
        String description = optionalString(row.getCell(9), sheetName, sourceExcelRow, "说明");

        LocalDateTime startDateTime = LocalDateTime.of(meetingDate, startTime);
        LocalDateTime endDateTime = LocalDateTime.of(meetingDate, endTime);
        if (!endDateTime.isAfter(startDateTime)) {
            throw invalidRecord(sheetName, sourceExcelRow, "结束时间必须晚于开始时间。");
        }

        return new LegacyImportRecord(
                sheetName,
                sourceExcelRow,
                "LEGACY-" + year + "-%06d".formatted(sourceExcelRow),
                roomName,
                organizerName,
                subject,
                attendeeCount,
                status,
                startDateTime,
                endDateTime,
                participantsText,
                description);
    }

    private boolean isBlankRow(Row row) {
        for (int index = 0; index < EXPECTED_HEADERS.size(); index++) {
            Cell cell = row.getCell(index);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private LocalDate readDate(Cell cell, String sheetName, Object sourceExcelRow, String columnName) {
        if (cell == null || cell.getCellType() != CellType.NUMERIC || !DateUtil.isCellDateFormatted(cell)) {
            throw invalidRecord(sheetName, sourceExcelRow, columnName + "必须为 Excel 日期。");
        }
        return cell.getLocalDateTimeCellValue().toLocalDate();
    }

    private LocalTime readTime(Cell cell, String sheetName, Object sourceExcelRow, String columnName) {
        String value = requiredString(cell, sheetName, sourceExcelRow, columnName);
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw invalidRecord(sheetName, sourceExcelRow, columnName + "必须为 HH:mm。");
        }
    }

    private String readStatus(Cell cell, String sheetName, int sourceExcelRow) {
        String status = requiredString(cell, sheetName, sourceExcelRow, "会议状态");
        if (!"ACTIVE".equals(status) && !"CANCELLED".equals(status)) {
            throw invalidRecord(sheetName, sourceExcelRow, "会议状态只能为 ACTIVE 或 CANCELLED。");
        }
        return status;
    }

    private Integer optionalAttendeeCount(Cell cell, String sheetName, int sourceExcelRow) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (value != Math.rint(value) || value < 0 || value > 65535) {
                throw invalidRecord(sheetName, sourceExcelRow, "参会人数必须为0至65535的整数。");
            }
            return (int) value;
        }
        if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank()) {
            return null;
        }
        throw invalidRecord(sheetName, sourceExcelRow, "参会人数必须为整数或为空。");
    }

    private int readPositiveInteger(Cell cell, String sheetName, Object sourceExcelRow, String columnName) {
        if (cell == null || cell.getCellType() != CellType.NUMERIC) {
            throw invalidRecord(sheetName, sourceExcelRow, columnName + "必须为正整数。");
        }
        double value = cell.getNumericCellValue();
        if (value != Math.rint(value) || value <= 0 || value > Integer.MAX_VALUE) {
            throw invalidRecord(sheetName, sourceExcelRow, columnName + "必须为正整数。");
        }
        return (int) value;
    }

    private String requiredString(Cell cell, String sheetName, Object sourceExcelRow, String columnName) {
        if (cell == null || cell.getCellType() != CellType.STRING || cell.getStringCellValue().isBlank()) {
            throw invalidRecord(sheetName, sourceExcelRow, columnName + "不能为空。");
        }
        return cell.getStringCellValue();
    }

    private String optionalString(Cell cell, String sheetName, int sourceExcelRow, String columnName) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() != CellType.STRING) {
            throw invalidRecord(sheetName, sourceExcelRow, columnName + "必须为文本或为空。");
        }
        String value = cell.getStringCellValue();
        return value.isBlank() ? null : value;
    }

    private IllegalArgumentException invalidRecord(String sheetName, Object sourceExcelRow, String reason) {
        return new IllegalArgumentException(sheetName + " / 原Excel行号" + sourceExcelRow + "：" + reason);
    }

    private record SheetDefinition(String year, int expectedRecordCount) {
    }
}
