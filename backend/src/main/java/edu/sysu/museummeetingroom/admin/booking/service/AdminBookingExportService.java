package edu.sysu.museummeetingroom.admin.booking.service;

import edu.sysu.museummeetingroom.admin.booking.mapper.AdminBookingExportMapper;
import edu.sysu.museummeetingroom.admin.booking.mapper.AdminBookingExportRow;
import edu.sysu.museummeetingroom.auth.CurrentUser;
import edu.sysu.museummeetingroom.auth.CurrentUserProvider;
import edu.sysu.museummeetingroom.booking.query.BookingListFilter;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminBookingExportService {

    private static final byte[] UTF_8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String LINE_SEPARATOR = "\r\n";
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final List<String> HEADERS = List.of(
            "预约号", "会议室", "预约人", "主题", "预计人数", "参会人员", "说明",
            "开始时间", "结束时间", "状态", "取消时间", "取消原因", "创建时间", "最后修改时间");

    private final CurrentUserProvider currentUserProvider;
    private final AdminBookingExportMapper adminBookingExportMapper;
    private final Clock businessClock;

    public byte[] export(
            String organizerKeyword,
            LocalDate date,
            String status,
            LocalDate fromDate,
            LocalDate toDate) {
        requireAdmin();
        validateDateCompatibility(date, fromDate, toDate);
        LocalDateTime now = LocalDateTime.now(businessClock);
        BookingListFilter filter = BookingListFilter.forAdminBookings(organizerKeyword, status, date, now);
        DateRange dateRange = resolveDateRange(date, fromDate, toDate);
        List<AdminBookingExportRow> rows = adminBookingExportMapper.findForExport(
                filter,
                dateRange.fromTime(),
                dateRange.toTime());
        StringBuilder csv = new StringBuilder();
        appendRow(csv, HEADERS);
        for (AdminBookingExportRow row : rows) {
            appendRow(csv, valuesOf(row));
        }
        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF_8_BOM.length + content.length];
        System.arraycopy(UTF_8_BOM, 0, result, 0, UTF_8_BOM.length);
        System.arraycopy(content, 0, result, UTF_8_BOM.length, content.length);
        return result;
    }

    private void requireAdmin() {
        CurrentUser currentUser = currentUserProvider.currentUser();
        if (!"ADMIN".equals(currentUser.roleCode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅管理员可导出预约记录。");
        }
    }

    private void validateDateCompatibility(LocalDate date, LocalDate fromDate, LocalDate toDate) {
        if (date != null && (fromDate != null || toDate != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_ERROR", "date 不能与 fromDate 或 toDate 同时使用。");
        }
    }

    private DateRange resolveDateRange(LocalDate date, LocalDate fromDate, LocalDate toDate) {
        if (date != null) {
            return new DateRange(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        }
        if (fromDate == null && toDate == null) {
            return new DateRange(null, null);
        }

        LocalDate today = LocalDate.now(businessClock);
        LocalDate resolvedFromDate = fromDate == null ? today : fromDate;
        LocalDate resolvedToDate = toDate == null ? today : toDate;
        if (resolvedFromDate.isAfter(resolvedToDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_ERROR", "导出开始日期不能晚于结束日期。");
        }
        return new DateRange(resolvedFromDate.atStartOfDay(), resolvedToDate.plusDays(1).atStartOfDay());
    }

    private record DateRange(LocalDateTime fromTime, LocalDateTime toTime) {}

    private List<String> valuesOf(AdminBookingExportRow row) {
        return List.of(
                row.bookingNo(),
                row.roomName(),
                row.organizerName(),
                row.subject(),
                nullableValue(row.attendeeCount()),
                nullableValue(row.participantsText()),
                nullableValue(row.description()),
                format(row.startTime()),
                format(row.endTime()),
                row.status(),
                format(row.cancelledAt()),
                nullableValue(row.cancelReason()),
                format(row.createdAt()),
                format(row.updatedAt()));
    }

    private String nullableValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private String format(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }

    private void appendRow(StringBuilder csv, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(escape(values.get(index)));
        }
        csv.append(LINE_SEPARATOR);
    }

    private String escape(String value) {
        String safeValue = preventsFormulaInjection(value) ? "'" + value : value;
        if (safeValue.indexOf(',') >= 0 || safeValue.indexOf('"') >= 0
                || safeValue.indexOf('\r') >= 0 || safeValue.indexOf('\n') >= 0) {
            return '"' + safeValue.replace("\"", "\"\"") + '"';
        }
        return safeValue;
    }

    private boolean preventsFormulaInjection(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isWhitespace(character)) {
                continue;
            }
            return character == '=' || character == '+' || character == '-' || character == '@';
        }
        return false;
    }
}
