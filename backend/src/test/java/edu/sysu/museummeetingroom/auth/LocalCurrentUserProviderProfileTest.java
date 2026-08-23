package edu.sysu.museummeetingroom.auth;

import static org.assertj.core.api.Assertions.assertThat;

import edu.sysu.museummeetingroom.user.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

class LocalCurrentUserProviderProfileTest {

    @Test
    void registersLocalCurrentUserProviderWhenLocalProfileIsActive() {
        contextRunnerWithProfile("local").run(context ->
                assertThat(context).hasSingleBean(LocalCurrentUserProvider.class));
    }

    @Test
    void doesNotRegisterLocalCurrentUserProviderWhenLocalProfileIsInactive() {
        new ApplicationContextRunner()
                .withUserConfiguration(LocalCurrentUserProviderTestConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(LocalCurrentUserProvider.class));
    }

    private ApplicationContextRunner contextRunnerWithProfile(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("testProperties", java.util.Map.of("local.current-user-id", "1"))))
                .withUserConfiguration(LocalCurrentUserProviderTestConfiguration.class);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(LocalCurrentUserProvider.class)
    static class LocalCurrentUserProviderTestConfiguration {

        @Bean
        SysUserMapper sysUserMapper() {
            return Mockito.mock(SysUserMapper.class);
        }
    }
}
