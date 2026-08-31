package edu.sysu.museummeetingroom.user.mapper;

public record UserRow(
        Long id,
        String displayName,
        String pinHash,
        String departmentName,
        String roleCode,
        String status) {}
