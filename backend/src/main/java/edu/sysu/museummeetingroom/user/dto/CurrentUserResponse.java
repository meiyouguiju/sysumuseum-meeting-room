package edu.sysu.museummeetingroom.user.dto;

public record CurrentUserResponse(Long id, String displayName, String departmentName, String roleCode, String status) {}
