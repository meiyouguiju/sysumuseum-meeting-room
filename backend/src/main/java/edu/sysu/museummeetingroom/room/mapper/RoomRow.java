package edu.sysu.museummeetingroom.room.mapper;

public record RoomRow(Long id, String name, String location, Integer capacity, String facilitiesText,
        String usageNotice, String status, Integer sortOrder) {}
