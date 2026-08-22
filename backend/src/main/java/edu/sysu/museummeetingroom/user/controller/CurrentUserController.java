package edu.sysu.museummeetingroom.user.controller;

import edu.sysu.museummeetingroom.user.dto.CurrentUserResponse;
import edu.sysu.museummeetingroom.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class CurrentUserController {

    private final UserService userService;

    @GetMapping
    public CurrentUserResponse getCurrentUser() {
        return userService.currentUser();
    }
}
