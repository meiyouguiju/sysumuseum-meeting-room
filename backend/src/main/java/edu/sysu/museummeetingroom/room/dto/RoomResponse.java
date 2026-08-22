package edu.sysu.museummeetingroom.room.dto;

public record RoomResponse(Long id, String name, String location, Integer capacity, String facilitiesText,
        String usageNotice, String status, Integer sortOrder) {}
