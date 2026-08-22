package edu.sysu.museummeetingroom.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sysu.museummeetingroom.auth.CurrentUser;
import edu.sysu.museummeetingroom.auth.CurrentUserProvider;
import edu.sysu.museummeetingroom.booking.command.CreateBookingCommand;
import edu.sysu.museummeetingroom.booking.dto.CreateBookingResult;
import edu.sysu.museummeetingroom.booking.mapper.BookingAuditLogEntity;
import edu.sysu.museummeetingroom.booking.mapper.BookingAuditLogMapper;
import edu.sysu.museummeetingroom.booking.mapper.BookingEntity;
import edu.sysu.museummeetingroom.booking.mapper.BookingMapper;
import edu.sysu.museummeetingroom.booking.mapper.BookingSlotEntity;
import edu.sysu.museummeetingroom.booking.mapper.BookingSlotMapper;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import edu.sysu.museummeetingroom.room.mapper.MeetingRoomMapper;
import edu.sysu.museummeetingroom.room.mapper.RoomRow;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final int SLOT_MINUTES = 30;
    private static final Duration MAXIMUM_DURATION = Duration.ofHours(5);
    private static final String ROOM_CAPACITY_EXCEEDED = "ROOM_CAPACITY_EXCEEDED";

    private final CurrentUserProvider currentUserProvider;
    private final MeetingRoomMapper meetingRoomMapper;
    private final BookingMapper bookingMapper;
    private final BookingSlotMapper bookingSlotMapper;
    private final BookingAuditLogMapper bookingAuditLogMapper;
    private final ObjectMapper objectMapper;
    private final Clock businessClock;

    @Transactional
    public CreateBookingResult create(CreateBookingCommand command) {
        CurrentUser currentUser = requireActiveCurrentUser();
        RoomRow room = requireEnabledRoom(command.roomId());
        LocalDateTime now = LocalDateTime.now(businessClock);
        validateTime(command, now);

        BookingEntity booking = createBookingEntity(command, currentUser, now);
        bookingMapper.insert(booking);

        List<BookingSlotEntity> slots = createSlots(booking.getId(), command.roomId(), command.startTime(), command.endTime());
        insertSlots(slots);
        writeCreateAudit(booking, currentUser, slots, now);

        return toResult(booking, room, currentUser);
    }

    private CurrentUser requireActiveCurrentUser() {
        CurrentUser currentUser = currentUserProvider.currentUser();
        if (!"ACTIVE".equals(currentUser.userStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "当前用户不可用。");
        }
        return currentUser;
    }

    private RoomRow requireEnabledRoom(Long roomId) {
        RoomRow room = roomId == null ? null : meetingRoomMapper.findById(roomId);
        if (room == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEETING_ROOM_NOT_FOUND", "会议室不存在。");
        }
        if (!"ENABLED".equals(room.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "MEETING_ROOM_DISABLED", "会议室已停用。");
        }
        return room;
    }

    private void validateTime(CreateBookingCommand command, LocalDateTime now) {
        LocalDateTime startTime = command.startTime();
        LocalDateTime endTime = command.endTime();
        if (startTime == null || endTime == null || !startTime.isBefore(endTime) || !isSlotBoundary(startTime) || !isSlotBoundary(endTime) || !startTime.isAfter(now)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BOOKING_TIME_INVALID", "预约时间不符合规则。");
        }
        if (!startTime.toLocalDate().equals(endTime.toLocalDate())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BOOKING_CROSS_DAY_NOT_ALLOWED", "预约不能跨自然日。");
        }
        LocalDate today = now.toLocalDate();
        if (startTime.toLocalDate().isAfter(today.plusDays(13))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BOOKING_WINDOW_EXCEEDED", "预约超出未来14天范围。");
        }
        Duration duration = Duration.between(startTime, endTime);
        if (duration.compareTo(Duration.ofMinutes(SLOT_MINUTES)) < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BOOKING_TIME_INVALID", "预约时长至少为30分钟。");
        }
        if (duration.compareTo(MAXIMUM_DURATION) > 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BOOKING_DURATION_EXCEEDED", "单次预约不能超过5小时。");
        }
    }

    private boolean isSlotBoundary(LocalDateTime time) {
        return time.getMinute() % SLOT_MINUTES == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }

    private BookingEntity createBookingEntity(CreateBookingCommand command, CurrentUser currentUser, LocalDateTime now) {
        BookingEntity booking = new BookingEntity();
        booking.setBookingNo(UUID.randomUUID().toString().replace("-", ""));
        booking.setRoomId(command.roomId());
        booking.setOrganizerUserId(currentUser.userId());
        booking.setOrganizerNameSnapshot(currentUser.displayName());
        booking.setSubject(command.subject());
        booking.setAttendeeCount(command.attendeeCount());
        booking.setParticipantsText(command.participantsText());
        booking.setDescription(command.description());
        booking.setStartTime(command.startTime());
        booking.setEndTime(command.endTime());
        booking.setLastModifiedByUserId(currentUser.userId());
        booking.setOccurredAt(now);
        return booking;
    }

    private List<BookingSlotEntity> createSlots(Long bookingId, Long roomId, LocalDateTime startTime, LocalDateTime endTime) {
        List<BookingSlotEntity> slots = new ArrayList<>();
        for (LocalDateTime slotStart = startTime; slotStart.isBefore(endTime); slotStart = slotStart.plusMinutes(SLOT_MINUTES)) {
            slots.add(new BookingSlotEntity(bookingId, roomId, slotStart));
        }
        return slots;
    }

    private void insertSlots(List<BookingSlotEntity> slots) {
        try {
            bookingSlotMapper.insertBatch(slots);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_SLOT_CONFLICT", "所选会议室的部分时间段已被预约。");
        }
    }

    private void writeCreateAudit(
            BookingEntity booking,
            CurrentUser currentUser,
            List<BookingSlotEntity> slots,
            LocalDateTime now) {
        BookingAuditSnapshot snapshot = BookingAuditSnapshot.from(booking);
        BookingAuditLogEntity auditLog = new BookingAuditLogEntity(
                booking.getId(),
                currentUser.userId(),
                currentUser.roleCode(),
                booking.getOrganizerUserId(),
                1,
                writeJson(snapshot),
                writeJson(slots),
                now);
        bookingAuditLogMapper.insertCreateAudit(auditLog);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化预约审计快照", exception);
        }
    }

    private CreateBookingResult toResult(BookingEntity booking, RoomRow room, CurrentUser currentUser) {
        List<CreateBookingResult.Warning> warnings = booking.getAttendeeCount() != null && booking.getAttendeeCount() > room.capacity()
                ? List.of(new CreateBookingResult.Warning(ROOM_CAPACITY_EXCEEDED, "预计人数超过会议室容量"))
                : List.of();
        return new CreateBookingResult(
                booking.getId(),
                booking.getBookingNo(),
                new CreateBookingResult.Room(room.id(), room.name()),
                new CreateBookingResult.Organizer(currentUser.userId(), currentUser.displayName()),
                booking.getSubject(),
                booking.getAttendeeCount(),
                booking.getParticipantsText(),
                booking.getDescription(),
                booking.getStartTime(),
                booking.getEndTime(),
                "ACTIVE",
                1,
                booking.getOccurredAt(),
                warnings);
    }

    private record BookingAuditSnapshot(
            Long id,
            String bookingNo,
            Long roomId,
            Long organizerUserId,
            String organizerNameSnapshot,
            String subject,
            Integer attendeeCount,
            String participantsText,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String status,
            Integer version,
            LocalDateTime createdAt) {

        private static BookingAuditSnapshot from(BookingEntity booking) {
            return new BookingAuditSnapshot(
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
                    "ACTIVE",
                    1,
                    booking.getOccurredAt());
        }
    }
}
