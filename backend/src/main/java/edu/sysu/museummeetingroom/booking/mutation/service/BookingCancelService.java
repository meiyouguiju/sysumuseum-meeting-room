package edu.sysu.museummeetingroom.booking.mutation.service;

import edu.sysu.museummeetingroom.auth.CurrentUser;
import edu.sysu.museummeetingroom.auth.CurrentUserProvider;
import edu.sysu.museummeetingroom.booking.mapper.BookingAuditLogMapper;
import edu.sysu.museummeetingroom.booking.mapper.BookingSlotMapper;
import edu.sysu.museummeetingroom.booking.mapper.BookingUpdateAuditLogEntity;
import edu.sysu.museummeetingroom.booking.mutation.mapper.BookingMutationMapper;
import edu.sysu.museummeetingroom.booking.query.mapper.BookingDetailRow;
import edu.sysu.museummeetingroom.booking.query.mapper.BookingQueryMapper;
import edu.sysu.museummeetingroom.booking.web.CancelBookingResponse;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import edu.sysu.museummeetingroom.maintenance.SlotTimeCalculator;
import java.time.Clock;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingCancelService {

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
        String normalizedReason = normalize(reason);
        CurrentUser user = currentUserProvider.currentUser();
        BookingDetailRow booking = bookingQueryMapper.findByIdForUpdate(bookingId);
        if (booking == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "预约不存在。");
        }
        if (!user.userId().equals(booking.organizerUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BOOKING_ACCESS_DENIED", "无权取消该预约。");
        }
        if ("CANCELLED".equals(booking.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_ALREADY_CANCELLED", "预约已取消。");
        }
        LocalDateTime now = LocalDateTime.now(businessClock);
        if (!now.isBefore(booking.endTime())) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_ALREADY_ENDED", "预约已结束。");
        }
        if (bookingMutationMapper.cancelWithVersion(bookingId, requestVersion, user.userId(), normalizedReason, now) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_VERSION_CONFLICT", "预约已被其他操作更新。");
        }
        CancelBookingResponse.SlotRelease release;
        if (now.isBefore(booking.startTime())) {
            bookingSlotMapper.deleteByBookingId(bookingId);
            release = new CancelBookingResponse.SlotRelease("IMMEDIATE", null, null);
        } else {
            LocalDateTime slot = slotTimeCalculator.currentSlotStart(now);
            if (bookingSlotMapper.holdCurrentSlot(bookingId, slot) != 1) {
                throw new IllegalStateException("进行中预约缺少当前占用槽");
            }
            bookingSlotMapper.deleteFutureSlots(bookingId, slot);
            release = new CancelBookingResponse.SlotRelease("AFTER_CURRENT_SLOT", slot, slot.plusMinutes(30));
        }
        writeAudit(booking, user, now, normalizedReason, release);
        return new CancelBookingResponse(bookingId, "CANCELLED", booking.version() + 1, now, release);
    }

    private void writeAudit(BookingDetailRow booking, CurrentUser user, LocalDateTime now,
                            String reason, CancelBookingResponse.SlotRelease release) {
        try {
            bookingAuditLogMapper.insertCancelAudit(new BookingUpdateAuditLogEntity(
                    booking.id(), user.userId(), user.roleCode(), booking.organizerUserId(), booking.version(),
                    booking.version() + 1, objectMapper.writeValueAsString(snapshot(booking, booking.status(), booking.version(), booking.cancelledAt(), booking.cancelReason())),
                    objectMapper.writeValueAsString(snapshot(booking, "CANCELLED", booking.version() + 1, now, reason)),
                    objectMapper.writeValueAsString(release), now));
        } catch (Exception exception) {
            throw new IllegalStateException("无法写入取消审计", exception);
        }
    }

    private Map<String, Object> snapshot(BookingDetailRow booking, String status, int version,
                                         LocalDateTime cancelledAt, String cancelReason) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", booking.id());
        snapshot.put("bookingNo", booking.bookingNo());
        snapshot.put("roomId", booking.roomId());
        snapshot.put("organizerUserId", booking.organizerUserId());
        snapshot.put("organizerName", booking.organizerName());
        snapshot.put("subject", booking.subject());
        snapshot.put("attendeeCount", booking.attendeeCount());
        snapshot.put("participantsText", booking.participantsText());
        snapshot.put("description", booking.description());
        snapshot.put("startTime", booking.startTime());
        snapshot.put("endTime", booking.endTime());
        snapshot.put("status", status);
        snapshot.put("version", version);
        snapshot.put("cancelledAt", cancelledAt);
        snapshot.put("cancelReason", cancelReason);
        return snapshot;
    }

    private String normalize(String reason) {
        if (reason == null) {
            return null;
        }
        String normalizedReason = reason.strip();
        if (normalizedReason.isEmpty()) {
            return null;
        }
        return normalizedReason;
    }
}
