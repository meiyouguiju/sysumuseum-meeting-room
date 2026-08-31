package edu.sysu.museummeetingroom.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "姓名不能为空。") @Size(max = 100, message = "姓名长度不能超过100个字符。") String name,
        @NotBlank(message = "PIN不能为空。") @Pattern(regexp = "\\d{4}", message = "PIN必须为4位数字。") String pin) {}
