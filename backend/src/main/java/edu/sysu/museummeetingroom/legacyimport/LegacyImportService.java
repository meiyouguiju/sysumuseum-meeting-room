package edu.sysu.museummeetingroom.legacyimport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sysu.museummeetingroom.booking.audit.BookingCreateAuditSnapshot;
import edu.sysu.museummeetingroom.booking.mapper.BookingAuditLogEntity;
import edu.sysu.museummeetingroom.booking.mapper.BookingAuditLogMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("legacy-import")
@RequiredArgsConstructor
public class LegacyImportService {

    private static final String TECHNICAL_USER_DISPLAY_NAME = "历史数据导入系统";
    private static final String ADMIN_ROLE = "ADMIN";

    private final LegacyImportMapper legacyImportMapper;
    private final BookingAuditLogMapper bookingAuditLogMapper;
    private final ObjectMapper objectMapper;
    private final Clock businessClock;

    @Transactional
    public void importRecords(List<LegacyImportRecord> records) {
        if (legacyImportMapper.countBookings() != 0) {
            throw new IllegalStateException("legacy-import 只能在空 booking 表上执行。");
        }

        Map<String, Long> userIdsByDisplayName = buildUserMap(legacyImportMapper.findAllUsers());
        Map<String, Long> roomIdsByName = buildRoomMap(legacyImportMapper.findAllRooms());
        List<ResolvedLegacyImportRecord> resolvedRecords = resolveRecords(records, userIdsByDisplayName, roomIdsByName);
        rejectExistingTechnicalUser(userIdsByDisplayName);

        LocalDateTime occurredAt = LocalDateTime.now(businessClock);
        LegacyImportTechnicalUser technicalUser = createTechnicalUser();
        legacyImportMapper.insertTechnicalUser(technicalUser);

        for (ResolvedLegacyImportRecord resolvedRecord : resolvedRecords) {
            LegacyImportBooking booking = createBooking(resolvedRecord, technicalUser.getId(), occurredAt);
            legacyImportMapper.insertBooking(booking);
            writeCreateAudit(booking, technicalUser.getId(), occurredAt);
        }
    }

    private Map<String, Long> buildUserMap(List<LegacyImportUserRow> users) {
        Map<String, Long> userIdsByDisplayName = new HashMap<>();
        for (LegacyImportUserRow user : users) {
            if (userIdsByDisplayName.putIfAbsent(user.displayName(), user.id()) != null) {
                throw new IllegalStateException("sys_user 存在重复 display_name，无法执行 legacy-import。");
            }
        }
        return userIdsByDisplayName;
    }

    private Map<String, Long> buildRoomMap(List<LegacyImportRoomRow> rooms) {
        Map<String, Long> roomIdsByName = new HashMap<>();
        for (LegacyImportRoomRow room : rooms) {
            if (roomIdsByName.putIfAbsent(room.name(), room.id()) != null) {
                throw new IllegalStateException("meeting_room 存在重复 name，无法执行 legacy-import。");
            }
        }
        return roomIdsByName;
    }

    private List<ResolvedLegacyImportRecord> resolveRecords(
            List<LegacyImportRecord> records,
            Map<String, Long> userIdsByDisplayName,
            Map<String, Long> roomIdsByName) {
        List<ResolvedLegacyImportRecord> resolvedRecords = new ArrayList<>();
        for (LegacyImportRecord record : records) {
            Long organizerUserId = userIdsByDisplayName.get(record.organizerName());
            if (organizerUserId == null) {
                throw invalidRecord(record, "找不到预订人。");
            }
            Long roomId = roomIdsByName.get(record.roomName());
            if (roomId == null) {
                throw invalidRecord(record, "找不到会议室。");
            }
            resolvedRecords.add(new ResolvedLegacyImportRecord(record, organizerUserId, roomId));
        }
        return List.copyOf(resolvedRecords);
    }

    private void rejectExistingTechnicalUser(Map<String, Long> userIdsByDisplayName) {
        if (userIdsByDisplayName.containsKey(TECHNICAL_USER_DISPLAY_NAME)) {
            throw new IllegalStateException("历史数据导入系统技术身份已存在，无法执行 legacy-import。");
        }
    }

    private LegacyImportTechnicalUser createTechnicalUser() {
        String externalSubject = UUID.randomUUID().toString();
        LegacyImportTechnicalUser technicalUser = new LegacyImportTechnicalUser();
        technicalUser.setExternalSubject(externalSubject);
        technicalUser.setLoginName("pin-" + externalSubject);
        return technicalUser;
    }

    private LegacyImportBooking createBooking(
            ResolvedLegacyImportRecord resolvedRecord,
            Long technicalUserId,
            LocalDateTime occurredAt) {
        LegacyImportRecord record = resolvedRecord.record();
        LegacyImportBooking booking = new LegacyImportBooking();
        booking.setBookingNo(record.bookingNo());
        booking.setRoomId(resolvedRecord.roomId());
        booking.setOrganizerUserId(resolvedRecord.organizerUserId());
        booking.setOrganizerNameSnapshot(record.organizerName());
        booking.setSubject(record.subject());
        booking.setAttendeeCount(record.attendeeCount());
        booking.setParticipantsText(record.participantsText());
        booking.setDescription(record.description());
        booking.setStartTime(record.startTime());
        booking.setEndTime(record.endTime());
        booking.setStatus(record.status());
        booking.setLastModifiedByUserId(technicalUserId);
        booking.setOccurredAt(occurredAt);
        return booking;
    }

    private void writeCreateAudit(LegacyImportBooking booking, Long technicalUserId, LocalDateTime occurredAt) {
        BookingAuditLogEntity auditLog = new BookingAuditLogEntity(
                booking.getId(),
                technicalUserId,
                ADMIN_ROLE,
                booking.getOrganizerUserId(),
                1,
                writeJson(new BookingCreateAuditSnapshot(
                        booking.getId(),
                        booking.getBookingNo(),
                        booking.getRoomId(),
                        booking.getOrganizerUserId(),
                        booking.getOrganizerNameSnapshot(),
                        booking.getSubject(),
                        booking.getAttendeeCount(),
                        booking.getParticipantsText(),
                        booking.getDescription(),
                        booking.getStartTime(),
                        booking.getEndTime(),
                        booking.getStatus(),
                        1,
                        booking.getOccurredAt())),
                null,
                occurredAt);
        bookingAuditLogMapper.insertCreateAudit(auditLog);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化历史预约审计快照。", exception);
        }
    }

    private IllegalArgumentException invalidRecord(LegacyImportRecord record, String reason) {
        return new IllegalArgumentException(
                record.sheetName() + " / 原Excel行号" + record.sourceExcelRow() + "：" + reason);
    }

    private record ResolvedLegacyImportRecord(LegacyImportRecord record, Long organizerUserId, Long roomId) {
    }

}
