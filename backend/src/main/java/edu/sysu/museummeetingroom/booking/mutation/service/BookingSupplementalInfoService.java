package edu.sysu.museummeetingroom.booking.mutation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sysu.museummeetingroom.auth.CurrentUser;
import edu.sysu.museummeetingroom.auth.CurrentUserProvider;
import edu.sysu.museummeetingroom.booking.command.SupplementalInfoCommand;
import edu.sysu.museummeetingroom.booking.mapper.BookingAuditLogMapper;
import edu.sysu.museummeetingroom.booking.mapper.BookingSupplementalInfoAuditLogEntity;
import edu.sysu.museummeetingroom.booking.mutation.mapper.BookingMutationMapper;
import edu.sysu.museummeetingroom.booking.query.dto.BookingDetailResponse;
import edu.sysu.museummeetingroom.booking.query.mapper.BookingDetailRow;
import edu.sysu.museummeetingroom.booking.query.mapper.BookingQueryMapper;
import edu.sysu.museummeetingroom.booking.query.service.BookingQueryService;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingSupplementalInfoService {

    private final CurrentUserProvider currentUserProvider;
    private final BookingQueryMapper bookingQueryMapper;
    private final BookingQueryService bookingQueryService;
    private final BookingMutationMapper bookingMutationMapper;
    private final BookingAuditLogMapper bookingAuditLogMapper;
    private final ObjectMapper objectMapper;
    private final Clock businessClock;

    @Transactional
    public BookingDetailResponse update(long bookingId, SupplementalInfoCommand command) {
        CurrentUser currentUser = requireActiveCurrentUser();
        BookingDetailRow before = requireBooking(bookingId);
        requireOwnerOrAdmin(before, currentUser);
        LocalDateTime occurredAt = LocalDateTime.now(businessClock);

        if (bookingMutationMapper.updateSupplementalInfoWithVersion(
                bookingId, command.version(), command, currentUser.userId(), occurredAt) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_VERSION_CONFLICT", "预约已被其他操作更新。");
        }

        writeAudit(before, command, currentUser, occurredAt);
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

    private void requireOwnerOrAdmin(BookingDetailRow booking, CurrentUser currentUser) {
        boolean isOwner = currentUser.userId().equals(booking.organizerUserId());
        boolean isAdmin = "ADMIN".equals(currentUser.roleCode());
        if (!isOwner && !isAdmin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BOOKING_ACCESS_DENIED", "无权修改该预约。");
        }
    }

    private void writeAudit(
            BookingDetailRow before,
            SupplementalInfoCommand command,
            CurrentUser currentUser,
            LocalDateTime occurredAt) {
        BookingSupplementalInfoAuditLogEntity auditLog = new BookingSupplementalInfoAuditLogEntity(
                before.id(),
                currentUser.userId(),
                currentUser.roleCode(),
                before.organizerUserId(),
                before.version(),
                before.version() + 1,
                writeJson(BookingSnapshot.from(before)),
                writeJson(BookingSnapshot.after(before, command, occurredAt)),
                occurredAt);
        bookingAuditLogMapper.insertSupplementalInfoUpdateAudit(auditLog);
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
            LocalDateTime cancelledAt,
            String cancelReason,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        private static BookingSnapshot from(BookingDetailRow booking) {
            return new BookingSnapshot(
                    booking.id(), booking.bookingNo(), booking.roomId(), booking.organizerUserId(), booking.organizerName(),
                    booking.subject(), booking.attendeeCount(), booking.participantsText(), booking.description(),
                    booking.startTime(), booking.endTime(), booking.status(), booking.version(), booking.cancelledAt(),
                    booking.cancelReason(), booking.createdAt(), booking.updatedAt());
        }

        private static BookingSnapshot after(
                BookingDetailRow before,
                SupplementalInfoCommand command,
                LocalDateTime occurredAt) {
            return new BookingSnapshot(
                    before.id(), before.bookingNo(), before.roomId(), before.organizerUserId(), before.organizerName(),
                    before.subject(), command.attendeeCount(), command.participantsText(), command.description(),
                    before.startTime(), before.endTime(), before.status(), before.version() + 1, before.cancelledAt(),
                    before.cancelReason(), before.createdAt(), occurredAt);
        }
    }
}
