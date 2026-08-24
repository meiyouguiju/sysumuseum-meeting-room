package edu.sysu.museummeetingroom.admin.room.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank(message = "name不能为空。") @Size(max = 120, message = "name长度不能超过120。") String name,
        @NotBlank(message = "location不能为空。") @Size(max = 200, message = "location长度不能超过200。") String location,
        @NotNull(message = "capacity不能为空。") @Positive(message = "capacity必须为正整数。")
        @Max(value = 65535, message = "capacity超出允许范围。") Integer capacity,
        String facilitiesText,
        String usageNotice,
        Integer sortOrder) {
}
