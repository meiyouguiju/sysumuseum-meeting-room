package edu.sysu.museummeetingroom.booking.mutation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sysu.museummeetingroom.auth.CurrentUser;
import edu.sysu.museummeetingroom.auth.CurrentUserProvider;
import edu.sysu.museummeetingroom.booking.command.CreateBookingCommand;
import edu.sysu.museummeetingroom.booking.command.UpdateBookingCommand;
import edu.sysu.museummeetingroom.booking.mapper.BookingSlotEntity;
import edu.sysu.museummeetingroom.booking.mapper.BookingSlotMapper;
import edu.sysu.museummeetingroom.booking.mapper.BookingUpdateAuditLogEntity;
import edu.sysu.museummeetingroom.booking.mapper.BookingAuditLogMapper;
import edu.sysu.museummeetingroom.booking.mutation.mapper.BookingMutationMapper;
import edu.sysu.museummeetingroom.booking.query.dto.BookingDetailResponse;
import edu.sysu.museummeetingroom.booking.query.mapper.BookingDetailRow;
import edu.sysu.museummeetingroom.booking.query.mapper.BookingQueryMapper;
import edu.sysu.museummeetingroom.booking.query.service.BookingQueryService;
import edu.sysu.museummeetingroom.booking.service.BookingSlotGenerator;
import edu.sysu.museummeetingroom.booking.service.BookingTimeRuleValidator;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import edu.sysu.museummeetingroom.room.mapper.MeetingRoomMapper;
import edu.sysu.museummeetingroom.room.mapper.RoomRow;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingUpdateService {

    private final CurrentUserProvider currentUserProvider;
    private final BookingQueryMapper bookingQueryMapper;
    private final BookingQueryService bookingQueryService;
    private final BookingMutationMapper bookingMutationMapper;
    private final MeetingRoomMapper meetingRoomMapper;
    private final BookingSlotMapper bookingSlotMapper;
    private final BookingSlotGenerator bookingSlotGenerator;
    private final BookingTimeRuleValidator bookingTimeRuleValidator;
    private final BookingAuditLogMapper bookingAuditLogMapper;
    private final ObjectMapper objectMapper;
    private final Clock businessClock;

    @Transactional
    public BookingDetailResponse update(long bookingId, UpdateBookingCommand command) {
        CurrentUser currentUser = requireActiveCurrentUser();
        BookingDetailRow before = requireBooking(bookingId);
        validateOwnerAndMutable(before, currentUser);
        boolean scheduleChanged = scheduleChanged(before, command);
        LocalDateTime now = LocalDateTime.now(businessClock);
        if (scheduleChanged) {
            requireEnabledRoom(command.roomId());
            bookingTimeRuleValidator.validate(toCreateCommand(command), now);
        }

        int updated = bookingMutationMapper.updateWithVersion(
                bookingId, command.version(), command, currentUser.userId(), now);
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_VERSION_CONFLICT", "预约已被其他操作更新。");
        }

        List<BookingSlotEntity> newSlots = List.of();
        if (scheduleChanged) {
            bookingSlotMapper.deleteByBookingId(bookingId);
            newSlots = bookingSlotGenerator.generate(bookingId, command.roomId(), command.startTime(), command.endTime());
            insertSlots(newSlots);
        }
        writeUpdateAudit(before, command, currentUser, now, scheduleChanged, newSlots);
        return bookingQueryService.getBookingDetail(bookingId);
    }

    private CurrentUser requireActiveCurrentUser() {
        CurrentUser currentUser = currentUserProvider.currentUser();
        if (!"ACTIVE".equals(currentUser.userStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "当前用户不可用。");
        }
        return currentUser;
    }

    private BookingDetailRow requireBooking(long bookingId) {
        BookingDetailRow booking = bookingQueryMapper.findByIdForUpdate(bookingId);
        if (booking == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "预约不存在。");
        }
        return booking;
    }

    private void validateOwnerAndMutable(BookingDetailRow booking, CurrentUser currentUser) {
        if (!currentUser.userId().equals(booking.organizerUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BOOKING_ACCESS_DENIED", "无权修改该预约。");
        }
        if ("CANCELLED".equals(booking.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_ALREADY_CANCELLED", "预约已取消。");
        }
        LocalDateTime now = LocalDateTime.now(businessClock);
        if (!now.isBefore(booking.startTime())) {
            if (!now.isBefore(booking.endTime())) {
                throw new ApiException(HttpStatus.CONFLICT, "BOOKING_ALREADY_ENDED", "预约已结束，不能修改。");
            }
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_ALREADY_STARTED", "预约已开始，不能修改。");
        }
    }

    private boolean scheduleChanged(BookingDetailRow booking, UpdateBookingCommand command) {
        return !booking.roomId().equals(command.roomId())
                || !booking.startTime().equals(command.startTime())
                || !booking.endTime().equals(command.endTime());
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

    private CreateBookingCommand toCreateCommand(UpdateBookingCommand command) {
        return new CreateBookingCommand(
                command.roomId(),
                command.subject(),
                command.startTime(),
                command.endTime(),
                command.attendeeCount(),
                command.participantsText(),
                command.description());
    }

    private void insertSlots(List<BookingSlotEntity> slots) {
        try {
            bookingSlotMapper.insertBatch(slots);
        } catch (DuplicateKeyException | DeadlockLoserDataAccessException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_SLOT_CONFLICT", "所选会议室的部分时间段已被预约。");
        }
    }

    private void writeUpdateAudit(
            BookingDetailRow before,
            UpdateBookingCommand command,
            CurrentUser currentUser,
            LocalDateTime occurredAt,
            boolean scheduleChanged,
            List<BookingSlotEntity> newSlots) {
        BookingSnapshot beforeSnapshot = BookingSnapshot.from(before);
        BookingSnapshot afterSnapshot = BookingSnapshot.after(before, command, occurredAt);
        BookingUpdateAuditLogEntity audit = new BookingUpdateAuditLogEntity(
                before.id(),
                currentUser.userId(),
                currentUser.roleCode(),
                before.organizerUserId(),
                before.version(),
                before.version() + 1,
                writeJson(beforeSnapshot),
                writeJson(afterSnapshot),
                writeJson(new SlotChange(before, command, scheduleChanged, newSlots)),
                occurredAt);
        bookingAuditLogMapper.insertUpdateAudit(audit);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化预约审计快照", exception);
        }
    }

    private record BookingSnapshot(
            Long id,
            String bookingNo,
            Long roomId,
            Long organizerUserId,
            String organizerName,
            String subject,
            Integer attendeeCount,
            String participantsText,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String status,
            Integer version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        private static BookingSnapshot from(BookingDetailRow booking) {
            return new BookingSnapshot(
                    booking.id(), booking.bookingNo(), booking.roomId(), booking.organizerUserId(), booking.organizerName(),
                    booking.subject(), booking.attendeeCount(), booking.participantsText(), booking.description(),
                    booking.startTime(), booking.endTime(), booking.status(), booking.version(), booking.createdAt(), booking.updatedAt());
        }

        private static BookingSnapshot after(BookingDetailRow before, UpdateBookingCommand command, LocalDateTime occurredAt) {
            return new BookingSnapshot(
                    before.id(), before.bookingNo(), command.roomId(), before.organizerUserId(), before.organizerName(),
                    command.subject(), command.attendeeCount(), command.participantsText(), command.description(),
                    command.startTime(), command.endTime(), before.status(), before.version() + 1,
                    before.createdAt(), occurredAt);
        }
    }

    private record SlotChange(
            boolean scheduleChanged,
            Long oldRoomId,
            Long newRoomId,
            LocalDateTime oldStartTime,
            LocalDateTime newStartTime,
            LocalDateTime oldEndTime,
            LocalDateTime newEndTime,
            List<BookingSlotEntity> insertedSlots) {

        private SlotChange(
                BookingDetailRow before,
                UpdateBookingCommand command,
                boolean scheduleChanged,
                List<BookingSlotEntity> insertedSlots) {
            this(scheduleChanged, before.roomId(), command.roomId(), before.startTime(), command.startTime(),
                    before.endTime(), command.endTime(), insertedSlots);
        }
    }
}
