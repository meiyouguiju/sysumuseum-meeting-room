package edu.sysu.museummeetingroom.user.service;

public record UserPinMaintenanceCommand(
        String action,
        String name,
        String pin,
        long userId,
        String roleCode) {
}
