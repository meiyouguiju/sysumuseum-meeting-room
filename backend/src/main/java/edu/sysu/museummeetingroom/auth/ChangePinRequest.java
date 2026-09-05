package edu.sysu.museummeetingroom.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePinRequest(
        @NotBlank(message = "当前 PIN 不能为空。") @Pattern(regexp = "\\d{4}", message = "当前 PIN 必须为4位数字。")
                String currentPin,
        @NotBlank(message = "新 PIN 不能为空。") @Pattern(regexp = "\\d{4}", message = "新 PIN 必须为4位数字。")
                String newPin) {}
