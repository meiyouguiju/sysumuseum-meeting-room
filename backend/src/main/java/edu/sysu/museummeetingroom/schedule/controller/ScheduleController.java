package edu.sysu.museummeetingroom.schedule.controller;

import edu.sysu.museummeetingroom.auth.CurrentUserProvider;
import edu.sysu.museummeetingroom.schedule.dto.ScheduleResponse;
import edu.sysu.museummeetingroom.schedule.service.ScheduleService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ScheduleResponse getSchedule(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        currentUserProvider.currentUser();
        return scheduleService.getSchedule(date);
    }
}
