package edu.sysu.museummeetingroom.legacyimport;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class LegacyImportWorkbookTestSupport {

    static final String[] HEADERS = {
        "会议日期", "会议主题", "会议室名称", "预订人", "参会人数", "会议状态",
        "开始时间", "结束时间", "参会人员", "说明", "原Excel行号"
    };

    private LegacyImportWorkbookTestSupport() {
    }

    static Path writeValidWorkbook(Path file) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            createSheet(workbook, "2026年", 168, 2026, dateStyle);
            createSheet(workbook, "2025", 235, 2025, dateStyle);
            createSheet(workbook, "2024", 439, 2024, dateStyle);
            try (OutputStream outputStream = java.nio.file.Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
        return file;
    }

    static Path writeWorkbookWithSheets(Path file, Map<String, Integer> sheets) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            for (Map.Entry<String, Integer> entry : sheets.entrySet()) {
                int year = resolveYear(entry.getKey());
                createSheet(workbook, entry.getKey(), entry.getValue(), year, dateStyle);
            }
            try (OutputStream outputStream = java.nio.file.Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
        return file;
    }

    static Map<String, Integer> expectedSheets() {
        Map<String, Integer> sheets = new LinkedHashMap<>();
        sheets.put("2026年", 168);
        sheets.put("2025", 235);
        sheets.put("2024", 439);
        return sheets;
    }

    private static int resolveYear(String sheetName) {
        if ("2026年".equals(sheetName)) {
            return 2026;
        }
        if (sheetName.matches("\\d{4}")) {
            return Integer.parseInt(sheetName);
        }
        return 2023;
    }

    private static void createSheet(
            XSSFWorkbook workbook,
            String name,
            int recordCount,
            int year,
            CellStyle dateStyle) {
        var sheet = workbook.createSheet(name);
        Row header = sheet.createRow(0);
        for (int index = 0; index < HEADERS.length; index++) {
            header.createCell(index).setCellValue(HEADERS[index]);
        }
        for (int index = 1; index <= recordCount; index++) {
            Row row = sheet.createRow(index);
            var dateCell = row.createCell(0);
            dateCell.setCellValue(Date.valueOf(LocalDate.of(year, 1, 1).plusDays(index - 1)));
            dateCell.setCellStyle(dateStyle);
            row.createCell(1).setCellValue("历史会议主题" + index);
            row.createCell(2).setCellValue("历史会议室A");
            row.createCell(3).setCellValue(index % 2 == 0 ? "历史预订人A" : "历史预订人B");
            if (index != 3) {
                row.createCell(4).setCellValue(index);
            }
            row.createCell(5).setCellValue("2024".equals(name) && index <= 2 ? "CANCELLED" : "ACTIVE");
            row.createCell(6).setCellValue("2026年".equals(name) && index == 1 ? "14:20" : "09:00");
            row.createCell(7).setCellValue("2026年".equals(name) && index == 1
                    ? "14:50"
                    : "2025".equals(name) && index == 1 ? "15:30" : "10:00");
            if (index == 2) {
                row.createCell(8).setCellValue("虚构参会人员");
            }
            if (index == 4) {
                row.createCell(9).setCellValue("虚构说明");
            }
            row.createCell(10).setCellValue(index + 1);
        }
    }
}
