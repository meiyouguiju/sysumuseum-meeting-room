package edu.sysu.museummeetingroom.admin.booking.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record AdminUpdateBookingRequest(
        @NotNull(message = "version不能为空。") @Positive(message = "version必须为正整数。") Integer version,
        @Positive(message = "roomId必须为正整数。") Long roomId,
        @NotBlank(message = "subject不能为空。") @Size(max = 200, message = "subject长度不能超过200。") String subject,
        LocalDateTime startTime,
        LocalDateTime endTime,
        @PositiveOrZero(message = "attendeeCount不能为负数。") @Max(value = 65535, message = "attendeeCount超出允许范围。") Integer attendeeCount,
        @Size(max = 2000, message = "participantsText长度不能超过2000。") String participantsText,
        @Size(max = 4000, message = "description长度不能超过4000。") String description,
        @Size(max = 500, message = "reason长度不能超过500。") String reason) {
}
