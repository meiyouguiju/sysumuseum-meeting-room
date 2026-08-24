package edu.sysu.museummeetingroom.admin.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sysu.museummeetingroom.admin.booking.command.AdminUpdateBookingCommand;
import edu.sysu.museummeetingroom.auth.CurrentUser;
import edu.sysu.museummeetingroom.auth.CurrentUserProvider;
import edu.sysu.museummeetingroom.booking.command.CreateBookingCommand;
import edu.sysu.museummeetingroom.booking.command.UpdateBookingCommand;
import edu.sysu.museummeetingroom.booking.mapper.AdminBookingUpdateAuditLogEntity;
import edu.sysu.museummeetingroom.booking.mapper.BookingAuditLogMapper;
import edu.sysu.museummeetingroom.booking.mapper.BookingSlotEntity;
import edu.sysu.museummeetingroom.booking.mapper.BookingSlotMapper;
import edu.sysu.museummeetingroom.booking.mutation.mapper.BookingMutationMapper;
import edu.sysu.museummeetingroom.booking.query.dto.BookingDetailResponse;
import edu.sysu.museummeetingroom.booking.query.mapper.BookingDetailRow;
import edu.sysu.museummeetingroom.booking.query.mapper.BookingQueryMapper;
import edu.sysu.museummeetingroom.booking.query.service.BookingQueryService;
import edu.sysu.museummeetingroom.booking.service.BookingSlotGenerator;
import edu.sysu.museummeetingroom.booking.service.BookingTimeRuleValidator;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import edu.sysu.museummeetingroom.room.mapper.MeetingRoomMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminBookingUpdateService {

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
    public BookingDetailResponse update(long bookingId, AdminUpdateBookingCommand command) {
        CurrentUser currentUser = requireAdmin();
        BookingDetailRow before = requireBooking(bookingId);
        LocalDateTime now = LocalDateTime.now(businessClock);
        requireMatchingVersion(before, command);
        validateState(before, now);
        requireReasonForAnotherUsersBooking(before, currentUser, command.reason());

        if (now.isBefore(before.startTime())) {
            return updateUpcoming(bookingId, command, before, currentUser, now);
        }
        return updateInProgress(bookingId, command, before, currentUser, now);
    }

    private BookingDetailResponse updateUpcoming(
            long bookingId,
            AdminUpdateBookingCommand command,
            BookingDetailRow before,
            CurrentUser currentUser,
            LocalDateTime now) {
        requireCompleteScheduleFields(command);
        boolean scheduleChanged = isScheduleChanged(before, command);
        if (scheduleChanged) {
            requireEnabledRoom(command.roomId());
            bookingTimeRuleValidator.validate(toCreateCommand(command), now);
        }

        List<BookingSlotEntity> insertedSlots = List.of();
        if (scheduleChanged) {
            bookingSlotMapper.deleteByBookingId(bookingId);
            insertedSlots = bookingSlotGenerator.generate(bookingId, command.roomId(), command.startTime(), command.endTime());
            insertSlots(insertedSlots);
        }
        updateCompleteBooking(bookingId, command, currentUser, now);
        writeAudit(before, command, currentUser, now, scheduleChanged, insertedSlots);
        return bookingQueryService.getBookingDetail(bookingId);
    }

    private BookingDetailResponse updateInProgress(
            long bookingId,
            AdminUpdateBookingCommand command,
            BookingDetailRow before,
            CurrentUser currentUser,
            LocalDateTime now) {
        if (command.roomId() != null || command.startTime() != null || command.endTime() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_STARTED_TIME_FIELDS_IMMUTABLE", "进行中预约不能修改会议室或时间。");
        }
        updateDetails(bookingId, command, currentUser, now);
        writeAudit(before, commandWithExistingSchedule(before, command), currentUser, now, false, List.of());
        return bookingQueryService.getBookingDetail(bookingId);
    }

    private CurrentUser requireAdmin() {
        CurrentUser currentUser = currentUserProvider.currentUser();
        if (!"ADMIN".equals(currentUser.roleCode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅管理员可修改预约。");
        }
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

    private void requireMatchingVersion(BookingDetailRow booking, AdminUpdateBookingCommand command) {
        if (!booking.version().equals(command.version())) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_VERSION_CONFLICT", "预约已被其他操作更新。");
        }
    }

    private void validateState(BookingDetailRow booking, LocalDateTime now) {
        if ("CANCELLED".equals(booking.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_ALREADY_CANCELLED", "预约已取消。");
        }
        if (!now.isBefore(booking.endTime())) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_ALREADY_ENDED", "预约已结束，不能修改。");
        }
    }

    private void requireReasonForAnotherUsersBooking(BookingDetailRow booking, CurrentUser currentUser, String reason) {
        if (!currentUser.userId().equals(booking.organizerUserId()) && reason == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_ERROR", "修改他人预约必须填写原因。");
        }
    }

    private void requireCompleteScheduleFields(AdminUpdateBookingCommand command) {
        if (command.roomId() == null || command.startTime() == null || command.endTime() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_ERROR", "未开始预约修改必须提交完整排期字段。");
        }
    }

    private boolean isScheduleChanged(BookingDetailRow before, AdminUpdateBookingCommand command) {
        return !before.roomId().equals(command.roomId())
                || !before.startTime().equals(command.startTime())
                || !before.endTime().equals(command.endTime());
    }

    private void requireEnabledRoom(Long roomId) {
        var room = meetingRoomMapper.findById(roomId);
        if (room == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEETING_ROOM_NOT_FOUND", "会议室不存在。");
        }
        if (!"ENABLED".equals(room.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "MEETING_ROOM_DISABLED", "会议室已停用。");
        }
    }

    private CreateBookingCommand toCreateCommand(AdminUpdateBookingCommand command) {
        return new CreateBookingCommand(command.roomId(), command.subject(), command.startTime(), command.endTime(),
                command.attendeeCount(), command.participantsText(), command.description());
    }

    private void insertSlots(List<BookingSlotEntity> slots) {
        try {
            bookingSlotMapper.insertBatch(slots);
        } catch (DuplicateKeyException | DeadlockLoserDataAccessException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_SLOT_CONFLICT", "所选会议室的部分时间段已被预约。");
        }
    }

    private void updateCompleteBooking(long bookingId, AdminUpdateBookingCommand command, CurrentUser currentUser,
            LocalDateTime now) {
        UpdateBookingCommand updateCommand = new UpdateBookingCommand(command.version(), command.roomId(), command.subject(),
                command.startTime(), command.endTime(), command.attendeeCount(), command.participantsText(), command.description());
        if (bookingMutationMapper.updateWithVersion(bookingId, command.version(), updateCommand, currentUser.userId(), now) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_VERSION_CONFLICT", "预约已被其他操作更新。");
        }
    }

    private void updateDetails(long bookingId, AdminUpdateBookingCommand command, CurrentUser currentUser,
            LocalDateTime now) {
        if (bookingMutationMapper.updateDetailsWithVersion(bookingId, command.version(), command, currentUser.userId(), now) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_VERSION_CONFLICT", "预约已被其他操作更新。");
        }
    }

    private AdminUpdateBookingCommand commandWithExistingSchedule(BookingDetailRow before, AdminUpdateBookingCommand command) {
        return new AdminUpdateBookingCommand(command.version(), before.roomId(), command.subject(), before.startTime(), before.endTime(),
                command.attendeeCount(), command.participantsText(), command.description(), command.reason());
    }

    private void writeAudit(BookingDetailRow before, AdminUpdateBookingCommand command, CurrentUser currentUser,
            LocalDateTime occurredAt, boolean scheduleChanged, List<BookingSlotEntity> insertedSlots) {
        bookingAuditLogMapper.insertAdminUpdateAudit(new AdminBookingUpdateAuditLogEntity(
                before.id(), currentUser.userId(), currentUser.roleCode(), before.organizerUserId(), command.reason(),
                before.version(), before.version() + 1, writeJson(snapshot(before, before.version())),
                writeJson(snapshotAfter(before, command)), writeJson(new SlotChange(before, command, scheduleChanged, insertedSlots)), occurredAt));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化预约审计快照", exception);
        }
    }

    private BookingSnapshot snapshot(BookingDetailRow booking, int version) {
        return new BookingSnapshot(booking.id(), booking.bookingNo(), booking.roomId(), booking.organizerUserId(), booking.organizerName(),
                booking.subject(), booking.attendeeCount(), booking.participantsText(), booking.description(), booking.startTime(), booking.endTime(),
                booking.status(), version, booking.cancelledAt(), booking.cancelReason());
    }

    private BookingSnapshot snapshotAfter(BookingDetailRow before, AdminUpdateBookingCommand command) {
        return new BookingSnapshot(before.id(), before.bookingNo(), command.roomId(), before.organizerUserId(), before.organizerName(),
                command.subject(), command.attendeeCount(), command.participantsText(), command.description(), command.startTime(), command.endTime(),
                before.status(), before.version() + 1, before.cancelledAt(), before.cancelReason());
    }

    private record BookingSnapshot(Long id, String bookingNo, Long roomId, Long organizerUserId, String organizerName,
            String subject, Integer attendeeCount, String participantsText, String description, LocalDateTime startTime,
            LocalDateTime endTime, String status, Integer version, LocalDateTime cancelledAt, String cancelReason) {
    }

    private record SlotChange(boolean scheduleChanged, Long oldRoomId, Long newRoomId, LocalDateTime oldStartTime,
            LocalDateTime newStartTime, LocalDateTime oldEndTime, LocalDateTime newEndTime, List<BookingSlotEntity> insertedSlots) {
        private SlotChange(BookingDetailRow before, AdminUpdateBookingCommand command, boolean scheduleChanged,
                List<BookingSlotEntity> insertedSlots) {
            this(scheduleChanged, before.roomId(), command.roomId(), before.startTime(), command.startTime(), before.endTime(),
                    command.endTime(), insertedSlots);
        }
    }
}
