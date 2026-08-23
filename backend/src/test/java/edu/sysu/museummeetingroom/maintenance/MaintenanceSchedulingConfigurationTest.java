package edu.sysu.museummeetingroom.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

class MaintenanceSchedulingConfigurationTest {

    @Test
    void registersSchedulerWhenSchedulingIsExplicitlyEnabled() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("testProperties", Map.of("maintenance.scheduling.enabled", "true"))))
                .withUserConfiguration(MaintenanceSchedulingTestConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(MaintenanceScheduler.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(MaintenanceSchedulingConfiguration.class)
    static class MaintenanceSchedulingTestConfiguration {

        @Bean
        MaintenanceScheduler maintenanceScheduler() {
            return new MaintenanceScheduler(
                    Mockito.mock(SlotCleanupService.class),
                    Mockito.mock(ProcessingRecoveryService.class),
                    Mockito.mock(IdempotencyCleanupService.class));
        }
    }
}
