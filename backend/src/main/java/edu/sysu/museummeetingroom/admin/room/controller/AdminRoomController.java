package edu.sysu.museummeetingroom.admin.room.controller;

import edu.sysu.museummeetingroom.admin.room.service.AdminRoomService;
import edu.sysu.museummeetingroom.admin.room.web.CreateRoomRequest;
import edu.sysu.museummeetingroom.admin.room.web.UpdateRoomRequest;
import edu.sysu.museummeetingroom.room.dto.RoomResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/rooms")
@RequiredArgsConstructor
public class AdminRoomController {

    private final AdminRoomService adminRoomService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(@Valid @RequestBody CreateRoomRequest request) {
        return adminRoomService.create(request);
    }

    @PatchMapping("/{roomId}")
    public RoomResponse update(@PathVariable long roomId, @Valid @RequestBody UpdateRoomRequest request) {
        return adminRoomService.update(roomId, request);
    }

    @PostMapping("/{roomId}/enable")
    public RoomResponse enable(@PathVariable long roomId) {
        return adminRoomService.enable(roomId);
    }

    @PostMapping("/{roomId}/disable")
    public RoomResponse disable(@PathVariable long roomId) {
        return adminRoomService.disable(roomId);
    }
}
