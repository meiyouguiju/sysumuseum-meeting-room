package edu.sysu.museummeetingroom.booking.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SupplementalInfoRequest(
        @NotNull(message = "version不能为空。") @Positive(message = "version必须为正整数。") Integer version,
        @PositiveOrZero(message = "attendeeCount不能为负数。") @Max(value = 65535, message = "attendeeCount超出允许范围。") Integer attendeeCount,
        @Size(max = 2000, message = "participantsText长度不能超过2000。") String participantsText,
        @Size(max = 4000, message = "description长度不能超过4000。") String description) {
}
