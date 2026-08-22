package edu.sysu.museummeetingroom.auth;

public record CurrentUser(Long userId, String displayName, String roleCode, String userStatus, String departmentName) {}
