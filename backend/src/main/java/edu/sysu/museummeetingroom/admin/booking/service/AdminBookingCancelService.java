package edu.sysu.museummeetingroom.admin.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sysu.museummeetingroom.auth.CurrentUser;
import edu.sysu.museummeetingroom.auth.CurrentUserProvider;
import edu.sysu.museummeetingroom.booking.mapper.BookingAuditLogMapper;
import edu.sysu.museummeetingroom.booking.mapper.BookingSlotMapper;
import edu.sysu.museummeetingroom.booking.mapper.AdminBookingCancelAuditLogEntity;
import edu.sysu.museummeetingroom.booking.mutation.mapper.BookingMutationMapper;
import edu.sysu.museummeetingroom.booking.query.mapper.BookingDetailRow;
import edu.sysu.museummeetingroom.booking.query.mapper.BookingQueryMapper;
import edu.sysu.museummeetingroom.booking.web.CancelBookingResponse;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import edu.sysu.museummeetingroom.maintenance.SlotTimeCalculator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminBookingCancelService {
    private final CurrentUserProvider currentUserProvider;
    private final BookingQueryMapper bookingQueryMapper;
    private final BookingMutationMapper bookingMutationMapper;
    private final BookingSlotMapper bookingSlotMapper;
    private final SlotTimeCalculator slotTimeCalculator;
    private final BookingAuditLogMapper bookingAuditLogMapper;
    private final ObjectMapper objectMapper;
    private final Clock businessClock;

    @Transactional
    public CancelBookingResponse cancel(long bookingId, int requestVersion, String reason) {
        CurrentUser user = requireAdmin();
        BookingDetailRow booking = requireBooking(bookingId);
        if ("CANCELLED".equals(booking.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_ALREADY_CANCELLED", "预约已取消。");
        }
        LocalDateTime now = LocalDateTime.now(businessClock);
        if (!now.isBefore(booking.endTime())) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_ALREADY_ENDED", "预约已结束。");
        }
        if (!booking.version().equals(requestVersion)) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_VERSION_CONFLICT", "预约已被其他操作更新。");
        }
        String normalizedReason = normalize(reason);
        if (!user.userId().equals(booking.organizerUserId()) && normalizedReason == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_ERROR", "取消他人预约必须填写原因。");
        }
        if (bookingMutationMapper.cancelWithVersion(bookingId, requestVersion, user.userId(), normalizedReason, now) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_VERSION_CONFLICT", "预约已被其他操作更新。");
        }
        CancelBookingResponse.SlotRelease release = releaseSlots(booking, now);
        writeAudit(booking, user, now, normalizedReason, release);
        return new CancelBookingResponse(bookingId, "CANCELLED", booking.version() + 1, now, release);
    }

    private CurrentUser requireAdmin() {
        CurrentUser user = currentUserProvider.currentUser();
        if (!"ADMIN".equals(user.roleCode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅管理员可取消预约。");
        }
        if (!"ACTIVE".equals(user.userStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "当前用户不可用。");
        }
        return user;
    }
    private BookingDetailRow requireBooking(long id) {
        BookingDetailRow booking = bookingQueryMapper.findByIdForUpdate(id);
        if (booking == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "预约不存在。");
        }
        return booking;
    }
    private CancelBookingResponse.SlotRelease releaseSlots(BookingDetailRow booking, LocalDateTime now) {
        if (now.isBefore(booking.startTime())) {
            bookingSlotMapper.deleteByBookingId(booking.id());
            return new CancelBookingResponse.SlotRelease("IMMEDIATE", null, null);
        }
        LocalDateTime slot = slotTimeCalculator.currentSlotStart(now);
        if (bookingSlotMapper.holdCurrentSlot(booking.id(), slot) != 1) {
            throw new IllegalStateException("进行中预约缺少当前占用槽");
        }
        bookingSlotMapper.deleteFutureSlots(booking.id(), slot);
        return new CancelBookingResponse.SlotRelease("AFTER_CURRENT_SLOT", slot, slot.plusMinutes(30));
    }
    private void writeAudit(BookingDetailRow booking, CurrentUser user, LocalDateTime now, String reason, CancelBookingResponse.SlotRelease release) {
        bookingAuditLogMapper.insertAdminCancelAudit(new AdminBookingCancelAuditLogEntity(
                booking.id(), user.userId(), user.roleCode(), booking.organizerUserId(), reason, booking.version(), booking.version() + 1,
                json(snapshot(booking, booking.status(), booking.version(), booking.cancelledAt(), booking.cancelReason())),
                json(snapshot(booking, "CANCELLED", booking.version() + 1, now, reason)), json(release), now));
    }
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法写入取消审计", exception);
        }
    }
    private Map<String, Object> snapshot(BookingDetailRow b, String status, int version, LocalDateTime cancelledAt, String cancelReason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", b.id());
        result.put("bookingNo", b.bookingNo());
        result.put("roomId", b.roomId());
        result.put("organizerUserId", b.organizerUserId());
        result.put("organizerName", b.organizerName());
        result.put("subject", b.subject());
        result.put("attendeeCount", b.attendeeCount());
        result.put("participantsText", b.participantsText());
        result.put("description", b.description());
        result.put("startTime", b.startTime());
        result.put("endTime", b.endTime());
        result.put("status", status);
        result.put("version", version);
        result.put("cancelledAt", cancelledAt);
        result.put("cancelReason", cancelReason);
        return result;
    }
    private String normalize(String reason) {
        if (reason == null) {
            return null;
        }
        String result = reason.strip();
        return result.isEmpty() ? null : result;
    }
}
