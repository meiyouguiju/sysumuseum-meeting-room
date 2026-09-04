package edu.sysu.museummeetingroom.maintenance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("!pin-maintenance & !user-bootstrap")
@EnableScheduling
@ConditionalOnProperty(prefix = "maintenance.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MaintenanceSchedulingConfiguration {
}
