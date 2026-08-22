package edu.sysu.museummeetingroom.room.controller;

import edu.sysu.museummeetingroom.auth.CurrentUserProvider;
import edu.sysu.museummeetingroom.room.dto.RoomResponse;
import edu.sysu.museummeetingroom.room.service.RoomService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public List<RoomResponse> findAll() {
        currentUserProvider.currentUser();
        return roomService.findAll();
    }
}
