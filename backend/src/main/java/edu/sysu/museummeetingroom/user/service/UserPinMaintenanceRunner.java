package edu.sysu.museummeetingroom.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("pin-maintenance")
@ConditionalOnProperty(name = "user-maintenance.enabled", havingValue = "true")
@RequiredArgsConstructor
public class UserPinMaintenanceRunner implements ApplicationRunner {

    private final ConfigurableApplicationContext applicationContext;
    private final UserPinMaintenanceService userPinMaintenanceService;

    @Value("${user-maintenance.action}")
    private String action;

    @Value("${user-maintenance.name:}")
    private String name;

    @Value("${user-maintenance.pin:}")
    private String pin;

    @Value("${user-maintenance.user-id:0}")
    private long userId;

    @Value("${user-maintenance.role-code:USER}")
    private String roleCode;

    @Override
    public void run(ApplicationArguments args) {
        userPinMaintenanceService.execute(new UserPinMaintenanceCommand(action, name, pin, userId, roleCode));
        applicationContext.close();
    }
}
