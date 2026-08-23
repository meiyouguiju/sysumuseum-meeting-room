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
import java.time.LocalDateTime;
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

    private static final String ROOM_CAPACITY_EXCEEDED = "ROOM_CAPACITY_EXCEEDED";

    private final CurrentUserProvider currentUserProvider;
    private final MeetingRoomMapper meetingRoomMapper;
    private final BookingMapper bookingMapper;
    private final BookingSlotMapper bookingSlotMapper;
    private final BookingAuditLogMapper bookingAuditLogMapper;
    private final BookingSlotGenerator bookingSlotGenerator;
    private final BookingTimeRuleValidator bookingTimeRuleValidator;
    private final ObjectMapper objectMapper;
    private final Clock businessClock;

    @Transactional
    public CreateBookingResult create(CreateBookingCommand command) {
        CurrentUser currentUser = requireActiveCurrentUser();
        RoomRow room = requireEnabledRoom(command.roomId());
        LocalDateTime now = LocalDateTime.now(businessClock);
        bookingTimeRuleValidator.validate(command, now);

        BookingEntity booking = createBookingEntity(command, currentUser, now);
        bookingMapper.insert(booking);

        List<BookingSlotEntity> slots = bookingSlotGenerator.generate(
                booking.getId(), command.roomId(), command.startTime(), command.endTime());
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
